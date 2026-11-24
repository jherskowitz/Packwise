package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

import org.json.JSONArray
import org.json.JSONObject

sealed interface UiState<out T> {
  data object Idle : UiState<Nothing>
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class Trip(
  @DocumentId val id: String = "",
  val userId: String = "",
  val destination: String = "",
  val country: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val startDateEpochDay: Long = 0L,
  val endDateEpochDay: Long = 0L,
  val durationDays: Long = 1L,
  val activities: List<String> = emptyList(),
  val calendarEventsSummary: List<String> = emptyList(),
  val weatherSummary: String = "",
  val weatherTempMinC: Double = 0.0,
  val weatherTempMaxC: Double = 0.0,
  val weatherRainProbMax: Long = 0L,
  val weatherAlertActive: Boolean = false,
  val weatherAlertMessage: String = "",
  val weatherNotificationsEnabled: Boolean = true,
  val status: String = "active",
  val resetCount: Long = 0L,
  val travelGuideJson: String = "",
  val headerImageUrl: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val travelGuide: TravelGuideInfo?
    get() = TravelGuideInfo.fromJsonString(travelGuideJson)

  fun formattedDestinationLabel(): String {
    val dest = destination.trim()
    val c = country.trim()
    if (c.isBlank()) return dest
    if (dest.endsWith(", $c", ignoreCase = true) || dest.equals(c, ignoreCase = true)) {
      return dest
    }
    return "$dest, $c"
  }
}

enum class TripSortOrder(val label: String) {
  CHRONOLOGICAL("Chronological"),
  RECENTLY_EDITED("Recently Edited"),
}

fun List<Trip>.sortedByOrder(sortOrder: TripSortOrder): List<Trip> = when (sortOrder) {
  TripSortOrder.CHRONOLOGICAL -> sortedWith(
    compareBy<Trip> { it.startDateEpochDay }
      .thenBy { it.endDateEpochDay }
      .thenByDescending { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
  )
  TripSortOrder.RECENTLY_EDITED -> sortedWith(
    compareByDescending<Trip> { (it.updatedAt ?: it.createdAt)?.toDate()?.time ?: 0L }
      .thenByDescending { it.startDateEpochDay }
  )
}

data class AttractionSuggestion(
  val name: String,
  val area: String = "",
  val description: String = "",
  val suggestedItem: String = "",
)

data class LocalEventSuggestion(
  val title: String,
  val timing: String = "",
  val venue: String = "",
  val description: String = "",
  val suggestedItem: String = "",
)

data class TravelGuideInfo(
  val isInternationalTravel: Boolean = false,
  val powerAdapterSummary: String = "",
  val powerAdapterChecklistItem: String = "",
  val esimSummary: String = "",
  val esimChecklistItem: String = "",
  val currencyAndPaymentTip: String = "",
  val transitTip: String = "",
  val entryAndSafetyTip: String = "",
  val nearbyAttractions: List<AttractionSuggestion> = emptyList(),
  val localEvents: List<LocalEventSuggestion> = emptyList(),
) {
  fun toJsonString(): String {
    val root = JSONObject().apply {
      put("isInternationalTravel", isInternationalTravel)
      put("powerAdapterSummary", powerAdapterSummary)
      put("powerAdapterChecklistItem", powerAdapterChecklistItem)
      put("esimSummary", esimSummary)
      put("esimChecklistItem", esimChecklistItem)
      put("currencyAndPaymentTip", currencyAndPaymentTip)
      put("transitTip", transitTip)
      put("entryAndSafetyTip", entryAndSafetyTip)
      put(
        "nearbyAttractions",
        JSONArray().apply {
          nearbyAttractions.forEach { attr ->
            put(
              JSONObject().apply {
                put("name", attr.name)
                put("area", attr.area)
                put("description", attr.description)
                put("suggestedItem", attr.suggestedItem)
              }
            )
          }
        }
      )
      put(
        "localEvents",
        JSONArray().apply {
          localEvents.forEach { ev ->
            put(
              JSONObject().apply {
                put("title", ev.title)
                put("timing", ev.timing)
                put("venue", ev.venue)
                put("description", ev.description)
                put("suggestedItem", ev.suggestedItem)
              }
            )
          }
        }
      )
    }
    return root.toString()
  }

  companion object {
    fun fromJsonString(raw: String): TravelGuideInfo? {
      if (raw.isBlank()) return null
      return runCatching {
        val obj = JSONObject(raw)
        val attractionsArr = obj.optJSONArray("nearbyAttractions") ?: JSONArray()
        val attractions = buildList {
          for (i in 0 until attractionsArr.length()) {
            val a = attractionsArr.optJSONObject(i) ?: continue
            val name = a.optString("name", "").trim()
            if (name.isNotBlank()) {
              add(
                AttractionSuggestion(
                  name = name,
                  area = a.optString("area", "").trim(),
                  description = a.optString("description", "").trim(),
                  suggestedItem = a.optString("suggestedItem", "").trim(),
                )
              )
            }
          }
        }
        val eventsArr = obj.optJSONArray("localEvents") ?: JSONArray()
        val events = buildList {
          for (i in 0 until eventsArr.length()) {
            val e = eventsArr.optJSONObject(i) ?: continue
            val title = e.optString("title", "").trim()
            if (title.isNotBlank()) {
              add(
                LocalEventSuggestion(
                  title = title,
                  timing = e.optString("timing", "").trim(),
                  venue = e.optString("venue", "").trim(),
                  description = e.optString("description", "").trim(),
                  suggestedItem = e.optString("suggestedItem", "").trim(),
                )
              )
            }
          }
        }
        val guide = TravelGuideInfo(
          isInternationalTravel = obj.optBoolean("isInternationalTravel", false),
          powerAdapterSummary = obj.optString("powerAdapterSummary", "").trim(),
          powerAdapterChecklistItem = obj.optString("powerAdapterChecklistItem", "").trim(),
          esimSummary = obj.optString("esimSummary", "").trim(),
          esimChecklistItem = obj.optString("esimChecklistItem", "").trim(),
          currencyAndPaymentTip = obj.optString("currencyAndPaymentTip", "").trim(),
          transitTip = obj.optString("transitTip", "").trim(),
          entryAndSafetyTip = obj.optString("entryAndSafetyTip", "").trim(),
          nearbyAttractions = attractions,
          localEvents = events,
        )
        if (
          guide.powerAdapterSummary.isBlank() &&
          guide.esimSummary.isBlank() &&
          guide.nearbyAttractions.isEmpty() &&
          guide.localEvents.isEmpty()
        ) {
          null
        } else {
          guide
        }
      }.getOrNull()
    }
  }
}

const val CATEGORY_ALWAYS_DO = "Always Do"
const val SOURCE_ALWAYS_DO = "always_do"
const val SOURCE_ALWAYS_BRING = "always_bring"

enum class VaultItemType {
  ALWAYS_BRING,
  ALWAYS_DO,
}

enum class TripItemScope {
  TRIP_SPECIFIC,
  ALWAYS_BRING,
  ALWAYS_DO,
}

enum class TasksImportTarget {
  SELECTED_TRIP,
  ALWAYS_BRING,
  ALWAYS_DO,
}

@Keep
@IgnoreExtraProperties
data class PackingItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val tripId: String = "",
  val name: String = "",
  val category: String = "Essentials",
  val quantity: Long = 1L,
  @get:PropertyName("isPacked") @PropertyName("isPacked") val isPacked: Boolean = false,
  @get:PropertyName("isAlwaysBring") @PropertyName("isAlwaysBring") val isAlwaysBring: Boolean = false,
  val sourceType: String = "manual",
  val contextReason: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isAlwaysDo: Boolean
    get() = sourceType == SOURCE_ALWAYS_DO || category.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)

  val scope: TripItemScope
    get() = when {
      isAlwaysDo -> TripItemScope.ALWAYS_DO
      isAlwaysBring -> TripItemScope.ALWAYS_BRING
      else -> TripItemScope.TRIP_SPECIFIC
    }
}

@Keep
@IgnoreExtraProperties
data class AlwaysBringItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val category: String = "Always Bring",
  val quantity: Long = 1L,
  val notes: String = "",
  val source: String = "manual",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val isAlwaysDo: Boolean
    get() = source == SOURCE_ALWAYS_DO || category.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)
}

data class DraftPackingItem(
  val name: String,
  val category: String,
  val quantity: Long = 1L,
  val isAlwaysBring: Boolean = false,
  val contextReason: String = "",
  val sourceType: String = "ai",
)

val DEFAULT_TRIP_ACTIVITIES = listOf(
  "City Walking",
  "Hiking",
  "Beach & Swim",
  "Business Meetings",
  "Evening Dining",
  "Workout / Run",
  "Photography",
  "Cold / Snow",
  "Camping",
  "Formal Event",
)

data class AiItemRecommendation(
  val engineLabel: String,
  val items: List<DraftPackingItem>,
  val focusActivities: List<String> = emptyList(),
)

data class DestinationPlace(
  val name: String,
  val country: String = "",
  val admin1: String = "",
  val latitude: Double,
  val longitude: Double,
) {
  fun cityAndRegionLabel(): String {
    val cleanCity = name.trim()
    val cleanRegion = admin1.trim()
    return if (cleanRegion.isNotBlank() && !cleanRegion.equals(cleanCity, ignoreCase = true)) {
      "$cleanCity, $cleanRegion"
    } else {
      cleanCity
    }
  }

  fun displayLabel(): String {
    val region = listOf(admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    return if (region.isNotBlank()) "$name, $region" else name
  }
}

data class DailyForecastPoint(
  val dateIso: String,
  val tempMinC: Double,
  val tempMaxC: Double,
  val rainProbPct: Int,
  val windMaxKmh: Double,
  val weatherCode: Int,
  val conditionLabel: String,
)

data class WeatherForecastSummary(
  val summaryText: String,
  val tempMinC: Double,
  val tempMaxC: Double,
  val rainProbMax: Long,
  val maxWindKmh: Double,
  val alertActive: Boolean,
  val alertMessage: String,
  val recommendedGear: List<String> = emptyList(),
  val dailyPoints: List<DailyForecastPoint> = emptyList(),
)

data class CalendarEventInfo(
  val id: String,
  val title: String,
  val startEpochMillis: Long,
  val endEpochMillis: Long,
  val location: String = "",
  val sourceApp: String = "Calendar",
  val suggestedItems: List<String> = emptyList(),
)

data class GoogleTaskListInfo(
  val id: String,
  val title: String,
)

data class GoogleTaskItemInfo(
  val id: String,
  val title: String,
  val notes: String = "",
  val isCompleted: Boolean = false,
)
