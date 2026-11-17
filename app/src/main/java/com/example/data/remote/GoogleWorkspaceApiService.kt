package com.example.data.remote

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.example.data.local.DeviceCalendarReader
import com.example.data.model.CalendarEventInfo
import com.example.data.model.GoogleTaskItemInfo
import com.example.data.model.GoogleTaskListInfo
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GoogleTaskListsResponse(
  val items: List<GoogleTaskListDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleTaskListDto(
  val id: String = "",
  val title: String = "",
)

@JsonClass(generateAdapter = true)
data class GoogleTasksResponse(
  val items: List<GoogleTaskItemDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleTaskItemDto(
  val id: String = "",
  val title: String? = null,
  val notes: String? = null,
  val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEventsResponse(
  val items: List<GoogleCalendarEventDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEventDto(
  val id: String = "",
  val summary: String? = null,
  val location: String? = null,
  val start: GoogleCalendarDateTimeDto? = null,
  val end: GoogleCalendarDateTimeDto? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarDateTimeDto(
  val dateTime: String? = null,
  val date: String? = null,
)

interface GoogleTasksApi {
  @GET("tasks/v1/users/@me/lists")
  suspend fun getTaskLists(
    @Header("Authorization") bearerToken: String,
    @Query("maxResults") maxResults: Int = 30,
  ): GoogleTaskListsResponse

  @GET("tasks/v1/lists/{tasklistId}/tasks")
  suspend fun getTasks(
    @Header("Authorization") bearerToken: String,
    @Path("tasklistId") tasklistId: String,
    @Query("showCompleted") showCompleted: Boolean = true,
    @Query("maxResults") maxResults: Int = 100,
  ): GoogleTasksResponse
}

interface GoogleCalendarApi {
  @GET("calendar/v3/calendars/primary/events")
  suspend fun getEvents(
    @Header("Authorization") bearerToken: String,
    @Query("timeMin") timeMinIso: String,
    @Query("timeMax") timeMaxIso: String,
    @Query("singleEvents") singleEvents: Boolean = true,
    @Query("orderBy") orderBy: String = "startTime",
    @Query("maxResults") maxResults: Int = 25,
  ): GoogleCalendarEventsResponse
}

sealed interface WorkspaceAuthState {
  data class Authorized(val accessToken: String) : WorkspaceAuthState
  data class NeedsConsent(val pendingIntent: PendingIntent) : WorkspaceAuthState
}

class GoogleWorkspaceRepository {
  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    .build()

  private val tasksApi: GoogleTasksApi = Retrofit.Builder()
    .baseUrl("https://tasks.googleapis.com/")
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(GoogleTasksApi::class.java)

  private val calendarApi: GoogleCalendarApi = Retrofit.Builder()
    .baseUrl("https://www.googleapis.com/")
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(GoogleCalendarApi::class.java)

  suspend fun authorizeScopes(
    activity: Activity,
    scopes: List<String>,
  ): Result<WorkspaceAuthState> = runCatching {
    val authRequest = AuthorizationRequest.builder()
      .setRequestedScopes(scopes.map { Scope(it) })
      .build()
    val result = Identity.getAuthorizationClient(activity)
      .authorize(authRequest)
      .await()
    if (result.hasResolution()) {
      val pi = result.pendingIntent
        ?: throw IllegalStateException("Authorization required, but no consent prompt was provided.")
      WorkspaceAuthState.NeedsConsent(pi)
    } else {
      val token = result.accessToken
        ?: throw IllegalStateException("Google authorization returned an empty access token.")
      WorkspaceAuthState.Authorized(token)
    }
  }.recoverCatching { err ->
    throw IllegalStateException(formatWorkspaceError(err, scopes), err)
  }

  fun extractAccessTokenFromIntent(activity: Activity, data: Intent?): Result<String> = runCatching {
    val result = Identity.getAuthorizationClient(activity)
      .getAuthorizationResultFromIntent(data)
    result.accessToken ?: throw IllegalStateException("No access token in authorization result.")
  }.recoverCatching { err ->
    throw IllegalStateException(formatWorkspaceError(err, emptyList()), err)
  }

  suspend fun fetchGoogleTaskLists(accessToken: String): Result<List<GoogleTaskListInfo>> = runCatching {
    val response = tasksApi.getTaskLists("Bearer $accessToken")
    response.items.orEmpty().map { GoogleTaskListInfo(id = it.id, title = it.title.ifBlank { "Untitled List" }) }
  }.recoverCatching { err ->
    throw IllegalStateException(formatApiFailure("Google Tasks", err), err)
  }

  suspend fun fetchTasksFromList(accessToken: String, listId: String): Result<List<GoogleTaskItemInfo>> = runCatching {
    val response = tasksApi.getTasks("Bearer $accessToken", listId)
    response.items.orEmpty()
      .filter { !it.title.isNullOrBlank() }
      .map { dto ->
        GoogleTaskItemInfo(
          id = dto.id,
          title = dto.title.orEmpty().trim(),
          notes = dto.notes.orEmpty().trim(),
          isCompleted = dto.status == "completed",
        )
      }
  }.recoverCatching { err ->
    throw IllegalStateException(formatApiFailure("Google Tasks", err), err)
  }

  suspend fun fetchGoogleCalendarEvents(
    accessToken: String,
    startEpochDay: Long,
    endEpochDay: Long,
  ): Result<List<CalendarEventInfo>> = runCatching {
    val timeMin = LocalDate.ofEpochDay(startEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    val timeMax = LocalDate.ofEpochDay(endEpochDay).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    val response = calendarApi.getEvents("Bearer $accessToken", timeMin, timeMax)
    response.items.orEmpty()
      .filter { !it.summary.isNullOrBlank() }
      .map { dto ->
        val title = dto.summary.orEmpty().trim()
        val loc = dto.location.orEmpty().trim()
        val startMs = parseEventMillis(dto.start)
        val endMs = parseEventMillis(dto.end).coerceAtLeast(startMs)
        CalendarEventInfo(
          id = dto.id,
          title = title,
          startEpochMillis = startMs,
          endEpochMillis = endMs,
          location = loc,
          sourceApp = "Google Calendar",
          suggestedItems = DeviceCalendarReader.suggestItemsForEvent(title, loc),
        )
      }
  }.recoverCatching { err ->
    throw IllegalStateException(formatApiFailure("Google Calendar", err), err)
  }

  private fun formatWorkspaceError(err: Throwable, scopes: List<String>): String {
    val apiEx = generateSequence(err) { it.cause }.filterIsInstance<ApiException>().firstOrNull()
    val statusCode = apiEx?.statusCode
    Log.w(TAG, "Google Workspace OAuth error (status=$statusCode, scopes=$scopes): ${err.message}", err)

    val isTasks = scopes.any { it.contains("tasks") }
    val serviceName = if (isTasks) "Google Tasks" else "Google Workspace"

    return when (statusCode) {
      CommonStatusCodes.DEVELOPER_ERROR -> {
        "Google blocked $serviceName OAuth (DEVELOPER_ERROR 10). On Android, OAuth uses the Cloud project owning package com.aistudio.packwise.trvlnx & its debug SHA-1 (gen-lang-client-0595459110), rather than a separate web OAuth project. " +
          if (isTasks) "Use the \"Paste / File Import\" tab below to import your list right now."
          else "Use \"Device Calendars\" to pull your synced Google Calendar without OAuth restrictions."
      }
      CommonStatusCodes.CANCELED -> {
        "$serviceName sign-in was cancelled or blocked by Google's unverified-app security check. " +
          "To allow direct OAuth, add your Google account as a Test User under OAuth Consent Screen and enable the Google Tasks/Calendar API in Google Cloud Console—or use " +
          if (isTasks) "\"Paste / File Import\" below right away."
          else "\"Device Calendars\" right away."
      }
      12500, CommonStatusCodes.SIGN_IN_REQUIRED -> {
        "Google security blocked direct $serviceName access (code $statusCode). Sensitive Google Workspace scopes require either app verification or your email listed under Test Users on the Android app's Google Cloud OAuth consent screen. " +
          if (isTasks) "Switch to \"Paste / File Import\" below to import your list immediately."
          else "Use \"Device Calendars\" instead—it reads your synced Google Calendar natively."
      }
      else -> {
        "Google $serviceName authorization was blocked${statusCode?.let { " (code $it)" } ?: ""}: ${err.localizedMessage ?: "Security / consent check failed"}. " +
          if (isTasks) "Use \"Paste / File Import\" below to import items right away."
          else "Use \"Device Calendars\" instead."
      }
    }
  }

  private fun formatApiFailure(serviceLabel: String, err: Throwable): String {
    val httpEx = generateSequence(err) { it.cause }.filterIsInstance<HttpException>().firstOrNull()
    Log.w(TAG, "$serviceLabel API error (http=${httpEx?.code()}): ${err.message}", err)
    return when (httpEx?.code()) {
      403 -> "$serviceLabel API returned 403 Forbidden. Enable the $serviceLabel API in your Google Cloud project and ensure your account is a Test User."
      401 -> "$serviceLabel authorization expired. Tap Retry to re-authorize."
      else -> "$serviceLabel request failed: ${err.localizedMessage ?: "Network error"}"
    }
  }

  private fun parseEventMillis(dt: GoogleCalendarDateTimeDto?): Long {
    if (dt == null) return System.currentTimeMillis()
    dt.dateTime?.let {
      runCatching { return Instant.parse(it).toEpochMilli() }
    }
    dt.date?.let {
      runCatching {
        return LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
      }
    }
    return System.currentTimeMillis()
  }

  companion object {
    private const val TAG = "GoogleWorkspaceRepo"
    const val SCOPE_TASKS_READONLY = "https://www.googleapis.com/auth/tasks.readonly"
    const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"

    /**
     * Parses checklists copied from Google Tasks, Keep, Notes, or exported Google Takeout Tasks.json / .txt / .csv.
     */
    fun parseTasksFromText(rawText: String): List<GoogleTaskItemInfo> {
      val trimmed = rawText.trim()
      if (trimmed.isBlank()) return emptyList()

      // Support Google Takeout Tasks.json or generic JSON task arrays
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        val jsonItems = parseTakeoutJson(trimmed)
        if (jsonItems.isNotEmpty()) return jsonItems
      }

      val results = mutableListOf<GoogleTaskItemInfo>()
      val seen = mutableSetOf<String>()

      trimmed.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEachIndexed { index, rawLine ->
          val isCompleted = rawLine.startsWith("[x]", ignoreCase = true) ||
            rawLine.startsWith("- [x]", ignoreCase = true) ||
            rawLine.startsWith("* [x]", ignoreCase = true) ||
            rawLine.startsWith("☑") ||
            rawLine.startsWith("✓")

          val cleaned = rawLine
            .replace(Regex("""^[-*•☐☑✓]\s*"""), "")
            .replace(Regex("""^\[[ xX]?\]\s*"""), "")
            .replace(Regex("""^\d+[.)]\s*"""), "")
            .trim()

          if (cleaned.isBlank()) return@forEachIndexed

          // Split optional notes after " — " or " - " or " | "
          val parts = cleaned.split(Regex("""\s+[—|\-]\s+"""), limit = 2)
          val title = parts[0].trim()
          val notes = parts.getOrNull(1)?.trim().orEmpty()

          if (title.isNotBlank() && seen.add(title.lowercase())) {
            results.add(
              GoogleTaskItemInfo(
                id = "paste_${index}_${title.lowercase().hashCode()}",
                title = title,
                notes = notes,
                isCompleted = isCompleted,
              )
            )
          }
        }
      return results
    }

    private fun parseTakeoutJson(jsonText: String): List<GoogleTaskItemInfo> = runCatching {
      val collected = mutableListOf<GoogleTaskItemInfo>()
      val seen = mutableSetOf<String>()

      fun extractFromArray(arr: JSONArray) {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val nestedItems = obj.optJSONArray("items")
          if (nestedItems != null && obj.optString("kind").contains("tasks#taskLists", ignoreCase = true).not() && !obj.has("title")) {
            extractFromArray(nestedItems)
            continue
          }
          if (nestedItems != null && nestedItems.length() > 0 && obj.optString("kind") == "tasks#taskList") {
            extractFromArray(nestedItems)
            continue
          }
          val title = obj.optString("title", "").trim()
          if (title.isNotBlank() && seen.add(title.lowercase())) {
            collected.add(
              GoogleTaskItemInfo(
                id = obj.optString("id", "json_${i}_${title.hashCode()}"),
                title = title,
                notes = obj.optString("notes", "").trim(),
                isCompleted = obj.optString("status") == "completed",
              )
            )
          }
          if (nestedItems != null) {
            extractFromArray(nestedItems)
          }
        }
      }

      if (jsonText.startsWith("[")) {
        extractFromArray(JSONArray(jsonText))
      } else {
        val root = JSONObject(jsonText)
        val itemsArr = root.optJSONArray("items")
        if (itemsArr != null) {
          extractFromArray(itemsArr)
        }
      }
      collected
    }.getOrElse { emptyList() }
  }
}
