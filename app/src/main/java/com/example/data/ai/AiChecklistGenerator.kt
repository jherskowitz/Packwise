package com.example.data.ai

import android.util.Log
import com.example.data.model.AlwaysBringItem
import com.example.data.model.CalendarEventInfo
import com.example.data.model.DraftPackingItem
import com.example.data.model.WeatherForecastSummary
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AiChecklistGenerator {

  private val checklistSchema = Schema.obj(
    properties = mapOf(
      "items" to Schema.array(
        items = Schema.obj(
          properties = mapOf(
            "name" to Schema.string(description = "Specific item to pack"),
            "category" to Schema.enumeration(
              values = listOf(
                "Clothing",
                "Outerwear",
                "Footwear",
                "Weather Gear",
                "Activity Gear",
                "Tech",
                "Toiletries",
                "Documents",
                "Health",
                "Essentials",
              ),
              description = "Packing category"
            ),
            "quantity" to Schema.integer(description = "Quantity tailored to trip duration (1 to 30)"),
            "contextReason" to Schema.string(description = "Short explanation tying item to weather, duration, activity, or calendar event"),
            "sourceType" to Schema.enumeration(
              values = listOf("weather", "duration", "activity", "calendar"),
              description = "Primary reason category for this item"
            ),
          )
        ),
        description = "Tailored trip-specific packing checklist items"
      )
    )
  )

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = checklistSchema
      }
    )
  }

  suspend fun generateChecklist(
    destination: String,
    country: String,
    durationDays: Long,
    activities: List<String>,
    weather: WeatherForecastSummary?,
    calendarEvents: List<CalendarEventInfo>,
    alwaysBringItems: List<AlwaysBringItem>,
  ): Result<List<DraftPackingItem>> {
    val weatherContext = weather?.let {
      "Forecast: ${it.summaryText}, min ${it.tempMinC}C, max ${it.tempMaxC}C, rain probability ${it.rainProbMax}%, max wind ${it.maxWindKmh} km/h. Alert: ${it.alertMessage.ifBlank { "None" }}"
    } ?: "Forecast unavailable; infer seasonal considerations for $destination, $country."

    val eventsContext = if (calendarEvents.isEmpty()) {
      "No planned calendar events linked."
    } else {
      calendarEvents.joinToString("; ") { "${it.title} (${it.location.ifBlank { "no location" }})" }
    }

    val alwaysBringNames = alwaysBringItems.joinToString(", ") { it.name }

    val prompt = """
      You are an expert travel packing strategist. Generate a practical, non-redundant trip-specific packing checklist.
      Destination: $destination, $country
      Trip Duration: $durationDays days (${(durationDays - 1).coerceAtLeast(0)} nights)
      Planned Activities: ${activities.ifEmpty { listOf("General sightseeing") }.joinToString(", ")}
      $weatherContext
      Planned Calendar Events: $eventsContext
      Items ALREADY in user's "Always Bring" vault (DO NOT duplicate these): $alwaysBringNames

      Rules:
      1. Scale clothing quantities realistically to $durationDays days (e.g. shirts, socks, undergarments).
      2. Include weather-responsive gear based on temperature range, rain probability, and wind.
      3. Include activity-specific gear for each selected activity.
      4. Include items specifically needed for the planned calendar events (e.g. formalwear for dinner/gala, adapters for presentations, trail shoes for hikes).
      5. Keep item names concise and clear. Return 12 to 24 high-value trip-context items.
    """.trimIndent()

    return try {
      val response = withTimeout(60_000L) {
        model.generateContent(prompt)
      }
      val jsonText = response.text
        ?: return Result.failure(IllegalStateException("Gemini returned an empty checklist response."))
      val root = JSONObject(jsonText)
      val arr = root.getJSONArray("items")
      val parsed = (0 until arr.length()).mapNotNull { idx ->
        val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
        val name = obj.optString("name").trim()
        if (name.isBlank()) return@mapNotNull null
        val category = obj.optString("category", "Essentials").ifBlank { "Essentials" }
        val quantity = obj.optLong("quantity", 1L).coerceIn(1L, 99L)
        val reason = obj.optString("contextReason", "Tailored for $destination").trim()
        val sourceType = obj.optString("sourceType", "activity")
          .takeIf { it in setOf("weather", "duration", "activity", "calendar") } ?: "activity"
        DraftPackingItem(
          name = name,
          category = category,
          quantity = quantity,
          isAlwaysBring = false,
          contextReason = reason,
          sourceType = sourceType,
        )
      }
      if (parsed.isEmpty()) {
        Result.failure(IllegalStateException("No valid packing items were generated."))
      } else {
        Result.success(parsed)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "AI checklist generation timed out", e)
      Result.failure(IllegalStateException("Checklist generation timed out after 60 seconds. Please try again."))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI checklist generation failed", e)
      Result.failure(e)
    }
  }

  private companion object {
    const val TAG = "AiChecklistGenerator"
  }
}
