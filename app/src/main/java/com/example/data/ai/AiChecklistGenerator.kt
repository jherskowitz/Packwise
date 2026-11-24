package com.example.data.ai

import android.util.Log
import com.example.data.model.CalendarEventInfo
import com.example.data.model.DraftPackingItem
import com.example.data.model.TravelGuideInfo
import com.example.data.model.WeatherForecastSummary
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedChecklistResult(
  val items: List<DraftPackingItem>,
  val travelGuide: TravelGuideInfo?,
  val engineLabel: String,
)

class AiChecklistGenerator(
  private val onDeviceNanoClient: OnDeviceNanoChecklistClient = OnDeviceNanoChecklistClient(),
) {

  private val travelGuideSchema = Schema.obj(
    properties = mapOf(
      "isInternationalTravel" to Schema.boolean(
        description = "True when destination typically involves cross-border travel, different voltage/plugs, or roaming considerations",
      ),
      "powerAdapterSummary" to Schema.string(
        description = "Plug type(s), socket compatibility, and mains voltage/frequency (e.g. Type C / F, 230V 50Hz)",
      ),
      "powerAdapterChecklistItem" to Schema.string(
        description = "Short actionable packing item for the right power adapter (e.g. Type C/F Travel Power Adapter)",
      ),
      "esimSummary" to Schema.string(
        description = "Actionable eSIM / local SIM or international roaming recommendation for this destination",
      ),
      "esimChecklistItem" to Schema.string(
        description = "Short actionable checklist item (e.g. Destination Data eSIM Plan)",
      ),
      "currencyAndPaymentTip" to Schema.string(
        description = "Local currency, contactless/card acceptance, and cash recommendation",
      ),
      "transitTip" to Schema.string(
        description = "Best local transit pass, IC card, or airport transfer tip",
      ),
      "entryAndSafetyTip" to Schema.string(
        description = "Entry requirements (e.g. passport validity, eTA/ETIAS/visa), health, or safety tip",
      ),
      "nearbyAttractions" to Schema.array(
        items = Schema.obj(
          properties = mapOf(
            "name" to Schema.string(description = "Attraction or neighborhood landmark name"),
            "area" to Schema.string(description = "District or distance context"),
            "description" to Schema.string(description = "Concise 1-sentence reason to visit"),
            "suggestedItem" to Schema.string(
              description = "Optional helpful gear item for visiting (e.g. Comfortable Walking Shoes, Modest Scarf), or empty string if none",
            ),
          ),
        ),
        description = "4 to 5 top nearby attractions for this destination",
      ),
      "localEvents" to Schema.array(
        items = Schema.obj(
          properties = mapOf(
            "title" to Schema.string(description = "Notable local event, festival, market, or seasonal happening"),
            "timing" to Schema.string(description = "Season, month, days of week, or time window matching trip dates"),
            "venue" to Schema.string(description = "Neighborhood or venue"),
            "description" to Schema.string(description = "Concise 1-sentence event overview"),
            "suggestedItem" to Schema.string(
              description = "Optional gear/attire item suited to this event, or empty string if none",
            ),
          ),
        ),
        description = "3 to 4 seasonal or recurring local events around the trip dates",
      ),
    ),
    description = "Destination travel prep guide covering power adapter, eSIM, international tips, nearby attractions, and local events",
  )

  private val checklistSchema = Schema.obj(
    properties = mapOf(
      "items" to Schema.array(
        items = Schema.obj(
          properties = mapOf(
            "name" to Schema.string(description = "Specific item to pack"),
            "category" to Schema.enumeration(
              values = listOf(
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
              ),
              description = "Packing category",
            ),
            "quantity" to Schema.integer(description = "Quantity tailored to trip duration (1 to 30)"),
            "sourceType" to Schema.enumeration(
              values = listOf("duration", "weather", "activity", "calendar", "ai"),
              description = "Primary reason category for this item",
            ),
            "contextReason" to Schema.string(
              description = "Short explanation tying item to weather, duration, activity, international travel, or event",
            ),
          ),
        ),
        description = "Tailored trip-specific packing checklist items",
      ),
      "travelGuide" to travelGuideSchema,
    ),
  )

  private val jsonGenerationConfig by lazy {
    generationConfig {
      responseMimeType = "application/json"
      responseSchema = checklistSchema
    }
  }

  private val guideOnlyGenerationConfig by lazy {
    generationConfig {
      responseMimeType = "application/json"
      responseSchema = travelGuideSchema
    }
  }

  private val primaryModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = PRIMARY_MODEL,
      generationConfig = jsonGenerationConfig,
    )
  }

  private val hydrogenFallbackModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = HYDROGEN_FALLBACK_MODEL,
      generationConfig = jsonGenerationConfig,
    )
  }

  private val liteFallbackModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = LITE_FALLBACK_MODEL,
      generationConfig = jsonGenerationConfig,
    )
  }

  private val guidePrimaryModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = PRIMARY_MODEL,
      generationConfig = guideOnlyGenerationConfig,
    )
  }

  private val guideHydrogenFallbackModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = HYDROGEN_FALLBACK_MODEL,
      generationConfig = guideOnlyGenerationConfig,
    )
  }

  private val guideLiteFallbackModel: GenerativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = LITE_FALLBACK_MODEL,
      generationConfig = guideOnlyGenerationConfig,
    )
  }

  suspend fun checkNanoStatus(): NanoAvailabilityStatus = onDeviceNanoClient.checkStatus()

  suspend fun downloadNanoModel(onProgress: (Long) -> Unit = {}): Result<Unit> =
    onDeviceNanoClient.downloadModel(onProgress)

  fun close() {
    onDeviceNanoClient.close()
  }

  suspend fun generateChecklist(
    destination: String,
    durationDays: Long,
    forecast: WeatherForecastSummary?,
    activities: List<String>,
    calendarEvents: List<CalendarEventInfo>,
    alwaysBringNames: List<String>,
    country: String = "",
    dateRangeLabel: String = "",
    homeCountry: String = "",
    isInternationalOverride: Boolean? = null,
    focusActivities: List<String> = emptyList(),
    onNanoDownloadable: (() -> Unit)? = null,
  ): Result<GeneratedChecklistResult> {
    val nanoStatus = onDeviceNanoClient.checkStatus()
    when (nanoStatus) {
      NanoAvailabilityStatus.AVAILABLE -> {
        val nanoResult = onDeviceNanoClient.generateChecklistOnDevice(
          destination = destination,
          durationDays = durationDays,
          forecast = forecast,
          activities = activities,
          calendarEvents = calendarEvents,
          alwaysBringNames = alwaysBringNames,
          homeCountry = homeCountry,
          isInternationalOverride = isInternationalOverride,
          focusActivities = focusActivities,
        )
        val nanoItems = nanoResult.getOrNull()
        if (!nanoItems.isNullOrEmpty()) {
          val guide = generateTravelGuideOnly(
            destination = destination,
            country = country,
            dateRangeLabel = dateRangeLabel,
            activities = activities,
            homeCountry = homeCountry,
            isInternationalOverride = isInternationalOverride,
          ).getOrNull()
          return Result.success(
            GeneratedChecklistResult(
              items = nanoItems,
              travelGuide = guide,
              engineLabel = "On-device Gemini Nano",
            )
          )
        }
        Log.w(
          TAG,
          "On-device Gemini Nano failed, falling back to Cloud Gemini: ${nanoResult.exceptionOrNull()?.message}",
        )
      }
      NanoAvailabilityStatus.DOWNLOADABLE -> {
        Log.i(TAG, "Gemini Nano model is downloadable; triggering background prep and using Cloud Gemini now")
        onNanoDownloadable?.invoke()
      }
      NanoAvailabilityStatus.DOWNLOADING -> {
        Log.i(TAG, "Gemini Nano model is downloading; using Cloud Gemini now")
      }
      NanoAvailabilityStatus.UNAVAILABLE -> {
        Log.d(TAG, "Gemini Nano unavailable on this device; using Cloud Gemini")
      }
    }

    val placeLabel = if (country.isNotBlank()) "$destination, $country" else destination
    val nights = (durationDays - 1L).coerceAtLeast(0L)
    val weatherLine = if (forecast != null) {
      "Forecast: ${forecast.summaryText}, min ${forecast.tempMinC}C, max ${forecast.tempMaxC}C, rain probability ${forecast.rainProbMax}%, max wind ${forecast.maxWindKmh} km/h. Alert: ${forecast.alertMessage.ifBlank { "None" }}"
    } else {
      "Forecast unavailable; infer seasonal considerations for $placeLabel."
    }

    val activitiesLine = activities.ifEmpty { listOf("General sightseeing") }.joinToString(", ")
    val calendarLine = if (calendarEvents.isEmpty()) {
      "No planned calendar events linked."
    } else {
      calendarEvents.joinToString("; ") { ev ->
        val loc = ev.location.ifBlank { "no location" }
        "${ev.title} ($loc)"
      }
    }
    val alwaysBringLine = if (alwaysBringNames.isEmpty()) "None" else alwaysBringNames.joinToString(", ")
    val datesLine = dateRangeLabel.ifBlank { "$durationDays days" }
    val homeBaseLine = homeCountry.ifBlank { "Unknown" }

    val internationalChecklistRule = when (isInternationalOverride) {
      false -> "4. This is a DOMESTIC trip within $homeBaseLine. Do NOT include travel power adapters, plug converters, eSIMs, international roaming, or passports."
      true -> "4. This is an INTERNATIONAL trip from $homeBaseLine to $placeLabel: include the destination's exact plug-type Power Adapter and eSIM / roaming plan."
      null -> "4. Compare $placeLabel against the traveler's home country ($homeBaseLine): ONLY include a plug power adapter and eSIM / roaming plan if traveling internationally."
    }

    val internationalGuideRule = when (isInternationalOverride) {
      false -> "1. This is a DOMESTIC trip within $homeBaseLine: set isInternationalTravel=false and leave powerAdapterSummary, powerAdapterChecklistItem, esimSummary, esimChecklistItem, and currencyAndPaymentTip as empty strings \"\"."
      true -> "1. This is an INTERNATIONAL trip from $homeBaseLine: set isInternationalTravel=true, recommend the exact travel power adapter plug type(s) & voltage/Hz, eSIM / connectivity option, local currency/payments, and passport/entry tips."
      null -> "1. Determine whether $placeLabel is international relative to the traveler's home country ($homeBaseLine). If domestic, set isInternationalTravel=false and leave adapter/eSIM/currency fields empty \"\". If international, set isInternationalTravel=true and populate plug adapter, voltage/Hz, eSIM, currency, and entry tips."
    }

    val focusActivitiesLine = if (focusActivities.isNotEmpty()) {
      "\n      Newly Added Activities (PRIORITIZE specific gear, footwear, attire, and safety items for these): ${focusActivities.joinToString(", ")}"
    } else {
      ""
    }

    val prompt = """
      Generate a realistic, tailored packing checklist AND destination travel guide for a trip to $placeLabel.
      Traveler Home Country: $homeBaseLine
      Trip Dates & Duration: $datesLine ($durationDays days, $nights nights)
      Planned Activities: $activitiesLine$focusActivitiesLine
      $weatherLine
      Planned Calendar Events: $calendarLine
      Items ALREADY in user's "Always Bring" / "Always-Do" vault (DO NOT duplicate these): $alwaysBringLine

      Checklist Rules:
      1. Scale clothing quantities realistically to $durationDays days.
      2. Include weather-appropriate outerwear/gear matching rain probability and min/max temperatures.
      3. Include dedicated items inspired by the planned activities${if (focusActivities.isNotEmpty()) "—especially the newly added activities (${focusActivities.joinToString(", ")})" else ""}, calendar events, and local/seasonal events.
      $internationalChecklistRule
      5. Do NOT duplicate items from the user's Always Bring / Always-Do vault above, and keep focus on travel items (12 to 22 items total).

      Travel Guide Rules:
      $internationalGuideRule
      2. Provide a practical local transit tip and any relevant safety/logistics tip.
      3. Suggest 4-5 top nearby attractions with area context and optional gear to pack.
      4. Suggest 3-4 notable local, cultural, festival, or seasonal events happening around $datesLine in $placeLabel with timing, venue, and optional gear.
    """.trimIndent()

    return try {
      val parsed = withTimeout(45_000L) {
        generateWithRetryAndFallback(
          prompt = prompt,
          destination = destination,
          alwaysBringNames = alwaysBringNames,
          isDomesticTrip = isInternationalOverride == false,
        )
      }
      Result.success(
        GeneratedChecklistResult(
          items = parsed.first,
          travelGuide = parsed.second,
          engineLabel = "Cloud Gemini",
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "AI checklist generation timed out", e)
      Result.failure(
        IllegalStateException("Checklist generation timed out after 45 seconds. Please try again.")
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI checklist generation failed", e)
      Result.failure(IllegalStateException(formatUserFacingError(e), e))
    }
  }

  suspend fun generateTravelGuideOnly(
    destination: String,
    country: String,
    dateRangeLabel: String,
    activities: List<String>,
    homeCountry: String = "",
    isInternationalOverride: Boolean? = null,
  ): Result<TravelGuideInfo> {
    val placeLabel = if (country.isNotBlank()) "$destination, $country" else destination
    val activitiesLine = activities.ifEmpty { listOf("Sightseeing") }.joinToString(", ")
    val datesLine = dateRangeLabel.ifBlank { "Upcoming trip" }
    val homeBaseLine = homeCountry.ifBlank { "Unknown" }

    val intlDirective = when (isInternationalOverride) {
      false -> "This is a DOMESTIC trip within $homeBaseLine. Set isInternationalTravel=false and leave powerAdapterSummary, powerAdapterChecklistItem, esimSummary, esimChecklistItem, and currencyAndPaymentTip as empty strings \"\"."
      true -> "This is an INTERNATIONAL trip from $homeBaseLine to $placeLabel. Set isInternationalTravel=true and include exact plug power adapter types & voltage/Hz, eSIM advice, currency & payment tips, and entry/passport requirements."
      null -> "Traveler Home Country: $homeBaseLine. If $placeLabel is domestic, set isInternationalTravel=false and leave powerAdapter/eSIM/currency empty \"\". If international, include plug adapter, voltage/Hz, eSIM, currency, and entry tips."
    }

    val prompt = """
      Build a concise, practical travel preparation guide for $placeLabel ($datesLine).
      Traveler Home Country: $homeBaseLine
      Traveler interests: $activitiesLine.
      $intlDirective
      Always include:
      - Local transit tip and relevant safety/logistics tip.
      - 4-5 top nearby attractions with area and optional helpful packing item.
      - 3-4 notable local, seasonal, or recurring events around $datesLine with timing, venue, and optional helpful packing item.
    """.trimIndent()

    val attempts: List<Pair<String, GenerativeModel>> = listOf(
      PRIMARY_MODEL to guidePrimaryModel,
      HYDROGEN_FALLBACK_MODEL to guideHydrogenFallbackModel,
      LITE_FALLBACK_MODEL to guideLiteFallbackModel,
    )

    var lastError: Exception? = null
    for ((index, entry) in attempts.withIndex()) {
      val (modelName, model) = entry
      if (index > 0) delay(300L)
      try {
        val response = withTimeout(18_000L) {
          model.generateContent(prompt)
        }
        val rawJson = response.text
          ?: throw IllegalStateException("Gemini returned an empty travel guide response.")
        val parsed = TravelGuideInfo.fromJsonString(rawJson)
          ?: throw IllegalStateException("Could not parse destination guide.")
        return Result.success(sanitizeGuideForScope(parsed, isInternationalOverride == false))
      } catch (e: TimeoutCancellationException) {
        lastError = e
        Log.w(TAG, "Guide attempt ${index + 1} with $modelName timed out")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        Log.w(TAG, "Guide attempt ${index + 1} with $modelName failed: ${e.message}")
        if (!isRetryableOrModelFallbackError(e)) break
      }
    }

    if (onDeviceNanoClient.checkStatus() == NanoAvailabilityStatus.AVAILABLE) {
      val nanoGuide = onDeviceNanoClient.generateTravelGuideOnDevice(
        destination = destination,
        country = country,
        dateRangeLabel = datesLine,
        activities = activities,
        homeCountry = homeCountry,
        isInternationalOverride = isInternationalOverride,
      )
      if (nanoGuide.isSuccess) return nanoGuide
    }

    return Result.failure(
      IllegalStateException(
        formatUserFacingError(lastError ?: IllegalStateException("Failed to load destination guide.")),
        lastError,
      )
    )
  }

  private suspend fun generateWithRetryAndFallback(
    prompt: String,
    destination: String,
    alwaysBringNames: List<String>,
    isDomesticTrip: Boolean,
  ): Pair<List<DraftPackingItem>, TravelGuideInfo?> {
    val attempts: List<Pair<String, GenerativeModel>> = listOf(
      PRIMARY_MODEL to primaryModel,
      HYDROGEN_FALLBACK_MODEL to hydrogenFallbackModel,
      LITE_FALLBACK_MODEL to liteFallbackModel,
    )

    var lastError: Exception? = null
    for ((index, entry) in attempts.withIndex()) {
      val (modelName, model) = entry
      if (index > 0) {
        delay(350L)
      }

      try {
        val response = withTimeout(22_000L) {
          model.generateContent(prompt)
        }
        val rawJson = response.text
          ?: throw IllegalStateException("Gemini returned an empty checklist response.")
        val parsed = parseChecklistAndGuideJson(
          rawJson = rawJson,
          destination = destination,
          alwaysBringNames = alwaysBringNames,
          isDomesticTrip = isDomesticTrip,
        )
        if (parsed.first.isNotEmpty()) {
          if (index > 0) {
            Log.i(TAG, "Checklist generated on attempt ${index + 1} using $modelName")
          }
          return parsed
        }
        lastError = IllegalStateException("No valid packing items were generated.")
      } catch (e: TimeoutCancellationException) {
        lastError = e
        Log.w(TAG, "Attempt ${index + 1} with $modelName timed out")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastError = e
        val retryable = isRetryableOrModelFallbackError(e)
        Log.w(TAG, "Attempt ${index + 1} with $modelName failed (retryable=$retryable): ${e.message}", e)
        if (!retryable) {
          throw e
        }
      }
    }

    throw lastError ?: IllegalStateException("Failed to generate checklist.")
  }

  private fun parseChecklistAndGuideJson(
    rawJson: String,
    destination: String,
    alwaysBringNames: List<String>,
    isDomesticTrip: Boolean,
  ): Pair<List<DraftPackingItem>, TravelGuideInfo?> {
    val json = JSONObject(rawJson)
    val guide = json.optJSONObject("travelGuide")?.let { guideObj ->
      TravelGuideInfo.fromJsonString(guideObj.toString())?.let {
        sanitizeGuideForScope(it, isDomesticTrip)
      }
    }
    val arr = json.optJSONArray("items") ?: return emptyList<DraftPackingItem>() to guide

    val parsed = mutableListOf<DraftPackingItem>()
    val seen = mutableSetOf<String>()
    alwaysBringNames.forEach { seen.add(it.trim().lowercase()) }

    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      val name = obj.optString("name", "").trim()
      if (name.isBlank() || !seen.add(name.lowercase())) continue
      if (isDomesticTrip && isAdapterOrEsimItemName(name)) continue
      val category = obj.optString("category", "Essentials").ifBlank { "Essentials" }
      val quantity = obj.optLong("quantity", 1L).coerceIn(1L, 30L)
      val sourceType = obj.optString("sourceType", "ai").ifBlank { "ai" }
      val contextReason = obj.optString("contextReason", "Tailored for $destination")
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

    return parsed to guide
  }

  private fun sanitizeGuideForScope(
    guide: TravelGuideInfo,
    isDomesticTrip: Boolean,
  ): TravelGuideInfo {
    if (!isDomesticTrip && guide.isInternationalTravel) return guide
    return guide.copy(
      isInternationalTravel = false,
      powerAdapterSummary = "",
      powerAdapterChecklistItem = "",
      esimSummary = "",
      esimChecklistItem = "",
      currencyAndPaymentTip = "",
    )
  }

  private fun isAdapterOrEsimItemName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.contains("adapter") ||
      lower.contains("plug converter") ||
      lower.contains("voltage converter") ||
      lower.contains("esim") ||
      lower.contains("e-sim")
  }

  private fun isRetryableOrModelFallbackError(error: Throwable): Boolean {
    val combined = generateSequence(error) { it.cause }
      .joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
      .lowercase()

    return combined.contains("high demand") ||
      combined.contains("overloaded") ||
      combined.contains("unavailable") ||
      combined.contains("503") ||
      combined.contains("429") ||
      combined.contains("404") ||
      combined.contains("not found") ||
      combined.contains("not supported") ||
      combined.contains("resource exhausted") ||
      combined.contains("rate limit") ||
      combined.contains("quota") ||
      combined.contains("too many requests") ||
      combined.contains("try again") ||
      combined.contains("serverexception") ||
      combined.contains("serviceunavailable") ||
      combined.contains("timed out") ||
      combined.contains("timeout")
  }

  private fun formatUserFacingError(error: Exception): String {
    val raw = error.localizedMessage ?: error.message ?: "Gemini AI failed to generate checklist."
    return if (isRetryableOrModelFallbackError(error)) {
      "Gemini servers are temporarily busy across available models ($raw). Tap Retry AI in a few seconds, or choose Skip AI to build your list with Always Bring, weather, and calendar gear."
    } else {
      raw
    }
  }

  private companion object {
    const val TAG = "AiChecklistGenerator"
    const val PRIMARY_MODEL = "gemini-3.8-flash"
    const val HYDROGEN_FALLBACK_MODEL = "gemini-4-hydrogen"
    const val LITE_FALLBACK_MODEL = "gemini-3.5-flash-lite"
  }
}
