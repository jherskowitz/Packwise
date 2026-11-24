package com.example.data.ai

import android.util.Log
import com.example.data.model.CalendarEventInfo
import com.example.data.model.DraftPackingItem
import com.example.data.model.TravelGuideInfo
import com.example.data.model.WeatherForecastSummary
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel as NanoGenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class NanoAvailabilityStatus {
  AVAILABLE,
  DOWNLOADABLE,
  DOWNLOADING,
  UNAVAILABLE,
}

class OnDeviceNanoChecklistClient {

  @Volatile
  private var clientInitialized = false

  private val nanoModel: NanoGenerativeModel by lazy {
    clientInitialized = true
    Generation.getClient()
  }

  suspend fun checkStatus(): NanoAvailabilityStatus {
    return try {
      val status = withTimeoutOrNull(4_000L) {
        nanoModel.checkStatus()
      }
      when (status) {
        FeatureStatus.AVAILABLE -> NanoAvailabilityStatus.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> NanoAvailabilityStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> NanoAvailabilityStatus.DOWNLOADING
        else -> NanoAvailabilityStatus.UNAVAILABLE
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Log.w(TAG, "Gemini Nano checkStatus unavailable on this device: ${e.message}")
      NanoAvailabilityStatus.UNAVAILABLE
    }
  }

  suspend fun downloadModel(
    onProgress: (Long) -> Unit = {},
  ): Result<Unit> {
    return try {
      var failureError: Throwable? = null
      nanoModel.download().collect { status ->
        when (status) {
          is DownloadStatus.DownloadStarted -> {
            Log.i(TAG, "Gemini Nano model download started")
            onProgress(0L)
          }
          is DownloadStatus.DownloadProgress -> {
            onProgress(status.totalBytesDownloaded)
          }
          DownloadStatus.DownloadCompleted -> {
            Log.i(TAG, "Gemini Nano model download completed")
          }
          is DownloadStatus.DownloadFailed -> {
            failureError = status.e
            Log.w(TAG, "Gemini Nano model download failed: ${status.e.message}", status.e)
          }
        }
      }
      if (failureError != null) {
        Result.failure(failureError!!)
      } else {
        Result.success(Unit)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Log.w(TAG, "Gemini Nano download threw exception: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun generateChecklistOnDevice(
    destination: String,
    durationDays: Long,
    forecast: WeatherForecastSummary?,
    activities: List<String>,
    calendarEvents: List<CalendarEventInfo>,
    alwaysBringNames: List<String>,
    homeCountry: String = "",
    isInternationalOverride: Boolean? = null,
    focusActivities: List<String> = emptyList(),
  ): Result<List<DraftPackingItem>> {
    if (checkStatus() != NanoAvailabilityStatus.AVAILABLE) {
      return Result.failure(IllegalStateException("On-device Gemini Nano is not ready."))
    }

    val nights = (durationDays - 1L).coerceAtLeast(0L)
    val weatherLine = if (forecast != null) {
      "${forecast.summaryText}, ${forecast.tempMinC}..${forecast.tempMaxC}C, rain ${forecast.rainProbMax}%"
    } else {
      "Seasonal weather for $destination"
    }
    val activitiesLine = activities.ifEmpty { listOf("Sightseeing") }.take(6).joinToString(", ")
    val calendarLine = calendarEvents.take(5).joinToString("; ") { ev ->
      if (ev.location.isNotBlank()) "${ev.title} (${ev.location})" else ev.title
    }.ifBlank { "None" }
    val excludedLine = alwaysBringNames.take(18).joinToString(", ").ifBlank { "None" }
    val intlGuidance = when {
      isInternationalOverride == false ->
        "This is a DOMESTIC trip within ${homeCountry.ifBlank { "the traveler's home country" }}. Do NOT include power adapters, voltage converters, eSIMs, or passports."
      homeCountry.isNotBlank() ->
        "Traveler is based in $homeCountry. Only include a local plug power adapter and eSIM/roaming gear if $destination is outside $homeCountry."
      else ->
        "Include the local plug power adapter and eSIM/roaming gear only if traveling internationally."
    }

    val focusLine = if (focusActivities.isNotEmpty()) {
      "\n      Newly added activities (prioritize specific gear/attire for these): ${focusActivities.joinToString(", ")}"
    } else {
      ""
    }
    val compactPrompt = """
      Return ONLY valid JSON with key "items" as an array of 10-16 trip packing objects for $destination ($durationDays days, $nights nights).
      Each object must have: "name", "category" (Clothing|Weather Gear|Footwear|Activity Gear|Tech|Toiletries|Documents|Health|Outerwear|Essentials), "quantity" (integer 1-15), "sourceType" (duration|weather|activity|calendar|ai), "contextReason".
      $intlGuidance
      Weather: $weatherLine
      Activities: $activitiesLine$focusLine
      Events: $calendarLine
      Skip existing items/chores: $excludedLine
      JSON:
    """.trimIndent()

    return try {
      runCatching {
        withTimeoutOrNull(4_000L) {
          nanoModel.warmup()
        }
      }

      val request = generateContentRequest(TextPart(compactPrompt)) {
        temperature = 0.2f
        maxOutputTokens = 768
      }

      val response = withTimeout(18_000L) {
        nanoModel.generateContent(request)
      }

      val text = response.candidates.firstOrNull()?.text.orEmpty()
      if (text.isBlank()) {
        return Result.failure(IllegalStateException("Gemini Nano returned an empty response."))
      }

      val items = parseFlexibleNanoOutput(
        rawText = text,
        destination = destination,
        alwaysBringNames = alwaysBringNames,
      ).let { parsed ->
        if (isInternationalOverride == false) {
          parsed.filterNot { isAdapterOrEsimItemName(it.name) }
        } else {
          parsed
        }
      }

      if (items.isEmpty()) {
        Result.failure(IllegalStateException("Gemini Nano produced no parseable packing items."))
      } else {
        Log.i(TAG, "Generated ${items.size} packing items on-device with Gemini Nano")
        Result.success(items)
      }
    } catch (e: TimeoutCancellationException) {
      Result.failure(IllegalStateException("On-device Gemini Nano timed out.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Log.w(TAG, "On-device Gemini Nano inference failed: ${e.message}", e)
      Result.failure(IllegalStateException(e.message ?: "On-device Gemini Nano failed.", e))
    }
  }

  suspend fun generateTravelGuideOnDevice(
    destination: String,
    country: String,
    dateRangeLabel: String,
    activities: List<String>,
    homeCountry: String = "",
    isInternationalOverride: Boolean? = null,
  ): Result<TravelGuideInfo> {
    if (checkStatus() != NanoAvailabilityStatus.AVAILABLE) {
      return Result.failure(IllegalStateException("On-device Gemini Nano is not ready."))
    }

    val placeLabel = if (country.isNotBlank()) "$destination, $country" else destination
    val activitiesLine = activities.ifEmpty { listOf("Sightseeing") }.take(5).joinToString(", ")
    val isKnownDomestic = isInternationalOverride == false
    val homeContext = when {
      isKnownDomestic ->
        "DOMESTIC trip within ${homeCountry.ifBlank { country }}. Set isInternationalTravel=false and leave powerAdapterSummary, powerAdapterChecklistItem, esimSummary, esimChecklistItem, currencyAndPaymentTip empty \"\"."
      homeCountry.isNotBlank() ->
        "Traveler home base: $homeCountry. Only set isInternationalTravel=true and include powerAdapter/esim/currency tips if $placeLabel is a different country from $homeCountry."
      else ->
        "Only set isInternationalTravel=true if international."
    }
    val prompt = """
      Return ONLY valid JSON for a travel prep guide to $placeLabel ($dateRangeLabel), interests: $activitiesLine.
      $homeContext
      Keys:
      "isInternationalTravel": boolean,
      "powerAdapterSummary": "exact plug types and voltage/Hz (empty if domestic)",
      "powerAdapterChecklistItem": "short packing item name for adapter (empty if domestic)",
      "esimSummary": "eSIM / mobile connectivity recommendation (empty if domestic)",
      "esimChecklistItem": "short checklist item for eSIM (empty if domestic)",
      "currencyAndPaymentTip": "local currency & card/cash tip (empty if domestic)",
      "transitTip": "best local transit or airport transfer tip",
      "entryAndSafetyTip": "relevant entry/safety reminder",
      "nearbyAttractions": [{"name","area","description","suggestedItem"}] (3-4 items),
      "localEvents": [{"title","timing","venue","description","suggestedItem"}] (2-3 seasonal/cultural events around $dateRangeLabel).
      JSON:
    """.trimIndent()

    return try {
      runCatching {
        withTimeoutOrNull(4_000L) {
          nanoModel.warmup()
        }
      }

      val request = generateContentRequest(TextPart(prompt)) {
        temperature = 0.2f
        maxOutputTokens = 768
      }

      val response = withTimeout(18_000L) {
        nanoModel.generateContent(request)
      }

      val cleaned = response.candidates.firstOrNull()?.text.orEmpty()
        .replace("```json", "", ignoreCase = true)
        .replace("```", "")
        .trim()
      val objStart = cleaned.indexOf('{')
      val objEnd = cleaned.lastIndexOf('}')
      val candidate = if (objStart >= 0 && objEnd > objStart) cleaned.substring(objStart, objEnd + 1) else cleaned
      val parsed = TravelGuideInfo.fromJsonString(candidate)
      if (parsed != null) {
        val sanitized = if (isKnownDomestic) {
          parsed.copy(
            isInternationalTravel = false,
            powerAdapterSummary = "",
            powerAdapterChecklistItem = "",
            esimSummary = "",
            esimChecklistItem = "",
            currencyAndPaymentTip = "",
          )
        } else {
          parsed
        }
        Result.success(sanitized)
      } else {
        Result.failure(IllegalStateException("Gemini Nano did not return a parseable travel guide."))
      }
    } catch (e: TimeoutCancellationException) {
      Result.failure(IllegalStateException("On-device Gemini Nano timed out.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      Log.w(TAG, "On-device Gemini Nano travel guide failed: ${e.message}", e)
      Result.failure(IllegalStateException(e.message ?: "On-device travel guide failed.", e))
    }
  }

  private fun parseFlexibleNanoOutput(
    rawText: String,
    destination: String,
    alwaysBringNames: List<String>,
  ): List<DraftPackingItem> {
    val cleaned = rawText
      .replace("```json", "", ignoreCase = true)
      .replace("```", "")
      .trim()

    val seen = mutableSetOf<String>()
    alwaysBringNames.forEach { seen.add(it.trim().lowercase(Locale.ROOT)) }

    val objStart = cleaned.indexOf('{')
    val objEnd = cleaned.lastIndexOf('}')
    if (objStart >= 0 && objEnd > objStart) {
      val candidate = cleaned.substring(objStart, objEnd + 1)
      val arr = runCatching { JSONObject(candidate).optJSONArray("items") }.getOrNull()
      if (arr != null) {
        val fromObject = parseJsonArray(arr, destination, seen)
        if (fromObject.isNotEmpty()) return fromObject
      }
    }

    val arrStart = cleaned.indexOf('[')
    val arrEnd = cleaned.lastIndexOf(']')
    if (arrStart >= 0 && arrEnd > arrStart) {
      val candidate = cleaned.substring(arrStart, arrEnd + 1)
      val arr = runCatching { JSONArray(candidate) }.getOrNull()
      if (arr != null) {
        val fromArray = parseJsonArray(arr, destination, seen)
        if (fromArray.isNotEmpty()) return fromArray
      }
    }

    return emptyList()
  }

  private fun parseJsonArray(
    arr: JSONArray,
    destination: String,
    seen: MutableSet<String>,
  ): List<DraftPackingItem> {
    val parsed = mutableListOf<DraftPackingItem>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      val name = obj.optString("name", "").trim()
      if (name.isBlank() || !seen.add(name.lowercase(Locale.ROOT))) continue

      val rawCat = obj.optString("category", "Essentials").trim()
      val category = if (rawCat in ALLOWED_CATEGORIES) rawCat else "Essentials"
      val quantity = obj.optLong("quantity", 1L).coerceIn(1L, 30L)
      val rawSource = obj.optString("sourceType", "ai").trim().lowercase(Locale.ROOT)
      val sourceType = if (rawSource in ALLOWED_SOURCES) rawSource else "ai"
      val contextReason = obj.optString("contextReason", "Suggested on-device for $destination")
        .ifBlank { "Suggested on-device for $destination" }

      parsed.add(
        DraftPackingItem(
          name = name,
          category = category,
          quantity = quantity,
          isAlwaysBring = false,
          contextReason = contextReason,
          sourceType = sourceType,
        )
      )
    }
    return parsed
  }

  fun close() {
    if (!clientInitialized) return
    runCatching { nanoModel.close() }
  }

  private fun isAdapterOrEsimItemName(name: String): Boolean {
    val lower = name.lowercase(Locale.ROOT)
    return lower.contains("adapter") ||
      lower.contains("plug converter") ||
      lower.contains("voltage converter") ||
      lower.contains("esim") ||
      lower.contains("e-sim")
  }

  private companion object {
    const val TAG = "OnDeviceNanoClient"
    val ALLOWED_CATEGORIES = setOf(
      "Clothing",
      "Weather Gear",
      "Footwear",
      "Activity Gear",
      "Tech",
      "Toiletries",
      "Documents",
      "Health",
      "Outerwear",
      "Essentials",
    )
    val ALLOWED_SOURCES = setOf("duration", "weather", "activity", "calendar", "ai")
  }
}
