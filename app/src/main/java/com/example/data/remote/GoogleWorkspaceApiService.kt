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
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
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
  val title: String? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleTasksResponse(
  val items: List<GoogleTaskDto>? = null,
  val nextPageToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleTaskDto(
  val id: String = "",
  val title: String? = null,
  val notes: String? = null,
  val status: String? = null,
  val hidden: Boolean? = null,
  val deleted: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEventsResponse(
  val items: List<GoogleCalendarEventDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEventDto(
  val id: String? = null,
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

interface GoogleTasksRestApi {
  @GET("tasks/v1/users/@me/lists")
  suspend fun listTaskLists(
    @Header("Authorization") bearerToken: String,
    @Query("maxResults") maxResults: Int = 20,
  ): GoogleTaskListsResponse

  @GET("tasks/v1/lists/{taskListId}/tasks")
  suspend fun listTasks(
    @Header("Authorization") bearerToken: String,
    @Path("taskListId") taskListId: String,
    @Query("showCompleted") showCompleted: Boolean = true,
    @Query("showHidden") showHidden: Boolean = true,
    @Query("maxResults") maxResults: Int = 100,
    @Query("pageToken") pageToken: String? = null,
  ): GoogleTasksResponse
}

interface GoogleCalendarRestApi {
  @GET("calendar/v3/calendars/primary/events")
  suspend fun listEvents(
    @Header("Authorization") bearerToken: String,
    @Query("timeMin") timeMinIso: String,
    @Query("timeMax") timeMaxIso: String,
    @Query("singleEvents") singleEvents: Boolean = true,
    @Query("orderBy") orderBy: String = "startTime",
    @Query("maxResults") maxResults: Int = 30,
  ): GoogleCalendarEventsResponse
}

sealed interface WorkspaceAuthState {
  data class Authorized(val accessToken: String) : WorkspaceAuthState
  data class NeedsConsent(val pendingIntent: PendingIntent) : WorkspaceAuthState
}

class GoogleWorkspaceRepository(
  tasksBaseUrl: String = "https://tasks.googleapis.com/",
  calendarBaseUrl: String = "https://www.googleapis.com/",
  client: OkHttpClient = defaultOkHttpClient(),
) {

  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val tasksApi: GoogleTasksRestApi = Retrofit.Builder()
    .baseUrl(tasksBaseUrl)
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(GoogleTasksRestApi::class.java)

  private val calendarApi: GoogleCalendarRestApi = Retrofit.Builder()
    .baseUrl(calendarBaseUrl)
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(GoogleCalendarRestApi::class.java)

  suspend fun authorizeScopes(
    activity: Activity,
    scopes: List<String>,
  ): Result<WorkspaceAuthState> {
    return try {
      val req = AuthorizationRequest.builder()
        .setRequestedScopes(scopes.map { Scope(it) })
        .build()
      val authResult = Identity.getAuthorizationClient(activity).authorize(req).await()
      if (authResult.hasResolution() && authResult.pendingIntent != null) {
        Result.success(WorkspaceAuthState.NeedsConsent(authResult.pendingIntent!!))
      } else {
        val token = authResult.accessToken
        if (token.isNullOrBlank()) {
          Result.failure(IllegalStateException("Google did not return an OAuth access token."))
        } else {
          Result.success(WorkspaceAuthState.Authorized(token))
        }
      }
    } catch (e: ApiException) {
      Log.w(TAG, "Google authorization failed with code ${e.statusCode}", e)
      Result.failure(
        IllegalStateException("Google OAuth unavailable on this build (code ${e.statusCode}). Use Paste or File import or Device Calendars.")
      )
    } catch (e: Exception) {
      Log.w(TAG, "Google authorization error", e)
      Result.failure(e)
    }
  }

  fun extractAccessTokenFromIntent(activity: Activity, data: Intent?): Result<String> {
    return try {
      val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
      val token = result.accessToken
      if (token.isNullOrBlank()) {
        Result.failure(IllegalStateException("No access token granted in authorization response."))
      } else {
        Result.success(token)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse authorization intent result", e)
      Result.failure(e)
    }
  }

  suspend fun fetchGoogleTaskLists(accessToken: String): Result<List<GoogleTaskListInfo>> = runCatching {
    val res = tasksApi.listTaskLists("Bearer $accessToken")
    res.items.orEmpty()
      .filter { it.id.isNotBlank() }
      .map { dto ->
        GoogleTaskListInfo(
          id = dto.id,
          title = dto.title?.ifBlank { "My Tasks" } ?: "My Tasks",
        )
      }
  }

  suspend fun fetchTasksFromList(
    accessToken: String,
    taskListId: String,
  ): Result<List<GoogleTaskItemInfo>> = runCatching {
    val collected = mutableListOf<GoogleTaskItemInfo>()
    var nextPageToken: String? = null
    var pages = 0

    do {
      val res = tasksApi.listTasks(
        bearerToken = "Bearer $accessToken",
        taskListId = taskListId,
        showCompleted = true,
        showHidden = true,
        pageToken = nextPageToken,
      )
      res.items.orEmpty()
        .filter { it.deleted != true && !it.title.isNullOrBlank() }
        .forEach { dto ->
          collected.add(
            GoogleTaskItemInfo(
              id = dto.id.ifBlank { "${dto.title}_${collected.size}" },
              title = dto.title!!.trim(),
              notes = dto.notes.orEmpty().trim(),
              isCompleted = dto.status == "completed",
            )
          )
        }
      nextPageToken = res.nextPageToken?.takeIf { it.isNotBlank() }
      pages++
    } while (nextPageToken != null && pages < 10)

    collected
  }

  suspend fun fetchGoogleCalendarEvents(
    accessToken: String,
    startEpochDay: Long,
    endEpochDay: Long,
  ): Result<List<CalendarEventInfo>> = runCatching {
    val timeMin = LocalDate.ofEpochDay(startEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    val timeMax = LocalDate.ofEpochDay(endEpochDay).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    val res = calendarApi.listEvents(
      bearerToken = "Bearer $accessToken",
      timeMinIso = timeMin,
      timeMaxIso = timeMax,
    )
    res.items.orEmpty()
      .filter { !it.summary.isNullOrBlank() }
      .map { dto ->
        val title = dto.summary!!.trim()
        val loc = dto.location.orEmpty().trim()
        val startMs = parseGoogleDateTime(dto.start)
        val endMs = parseGoogleDateTime(dto.end)
        CalendarEventInfo(
          id = dto.id ?: "${title}_$startMs",
          title = title,
          startEpochMillis = startMs,
          endEpochMillis = endMs,
          location = loc,
          sourceApp = "Google Calendar",
          suggestedItems = DeviceCalendarReader.suggestItemsForEvent(title, loc),
        )
      }
  }

  private fun parseGoogleDateTime(dto: GoogleCalendarDateTimeDto?): Long {
    if (dto == null) return 0L
    val dt = dto.dateTime
    if (!dt.isNullOrBlank()) {
      return runCatching { Instant.parse(dt).toEpochMilli() }.getOrDefault(0L)
    }
    val d = dto.date
    if (!d.isNullOrBlank()) {
      return runCatching {
        LocalDate.parse(d).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
      }.getOrDefault(0L)
    }
    return 0L
  }

  companion object {
    private const val TAG = "GoogleWorkspaceRepo"
    const val SCOPE_TASKS_READONLY = "https://www.googleapis.com/auth/tasks.readonly"
    const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"

    private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
      .build()

    fun parseTasksFromText(rawInput: String): List<GoogleTaskItemInfo> {
      val trimmed = rawInput.trim()
      if (trimmed.isBlank()) return emptyList()

      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        val jsonParsed = tryParseTakeoutJson(trimmed)
        if (jsonParsed.isNotEmpty()) return jsonParsed
      }

      val results = mutableListOf<GoogleTaskItemInfo>()
      val seen = mutableSetOf<String>()
      val checkboxRegex = Regex("""^[-*•+]?\s*\[(x|X|\s)?\]\s*(.+)$""")
      val bulletRegex = Regex("""^(?:[-*•+]|\d+[.)])\s+(.+)$""")

      trimmed.lineSequence().forEachIndexed { idx, line ->
        var clean = line.trim()
        if (clean.isBlank() || clean.startsWith("#")) return@forEachIndexed

        var isCompleted = false
        val cbMatch = checkboxRegex.matchEntire(clean)
        if (cbMatch != null) {
          isCompleted = cbMatch.groupValues[1].equals("x", ignoreCase = true)
          clean = cbMatch.groupValues[2].trim()
        } else {
          val bulletMatch = bulletRegex.matchEntire(clean)
          if (bulletMatch != null) {
            clean = bulletMatch.groupValues[1].trim()
          }
        }

        if (clean.isBlank()) return@forEachIndexed

        val sepParts = clean.split(Regex("""\s+[—–|-]\s+"""), limit = 2)
        val title = sepParts[0].trim()
        val notes = sepParts.getOrNull(1)?.trim().orEmpty()

        if (title.isNotBlank() && seen.add(title.lowercase())) {
          results.add(
            GoogleTaskItemInfo(
              id = "parsed_$idx",
              title = title,
              notes = notes,
              isCompleted = isCompleted,
            )
          )
        }
      }
      return results
    }

    private fun tryParseTakeoutJson(rawJson: String): List<GoogleTaskItemInfo> {
      return runCatching {
        val out = mutableListOf<GoogleTaskItemInfo>()
        val seen = mutableSetOf<String>()

        fun extractArray(arr: JSONArray) {
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val nested = obj.optJSONArray("items")
            if (nested != null) {
              extractArray(nested)
              continue
            }
            val title = obj.optString("title", "").trim()
            val deleted = obj.optBoolean("deleted", false)
            if (!deleted && title.isNotBlank() && seen.add(title.lowercase())) {
              out.add(
                GoogleTaskItemInfo(
                  id = obj.optString("id", "json_${out.size}"),
                  title = title,
                  notes = obj.optString("notes", "").trim(),
                  isCompleted = obj.optString("status", "") == "completed",
                )
              )
            }
          }
        }

        if (rawJson.startsWith("{")) {
          val root = JSONObject(rawJson)
          root.optJSONArray("items")?.let { extractArray(it) }
        } else {
          extractArray(JSONArray(rawJson))
        }
        out
      }.getOrDefault(emptyList())
    }
  }
}
