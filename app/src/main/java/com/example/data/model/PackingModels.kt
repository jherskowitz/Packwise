package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Idle : UiState<Nothing>
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class AlwaysBringItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val category: String = "Essentials",
  val quantity: Long = 1L,
  val notes: String = "",
  val source: String = "custom",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

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
  val weatherTempMinC: Double = 15.0,
  val weatherTempMaxC: Double = 22.0,
  val weatherRainProbMax: Long = 0L,
  val weatherAlertActive: Boolean = false,
  val weatherAlertMessage: String = "",
  val status: String = "active",
  val resetCount: Long = 0L,
  val weatherNotificationsEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PackingItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val tripId: String = "",
  val name: String = "",
  val category: String = "General",
  val quantity: Long = 1L,
  @get:PropertyName("isPacked")
  @field:PropertyName("isPacked")
  val isPacked: Boolean = false,
  @get:PropertyName("isAlwaysBring")
  @field:PropertyName("isAlwaysBring")
  val isAlwaysBring: Boolean = false,
  val contextReason: String = "",
  val sourceType: String = "custom",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class DraftPackingItem(
  val name: String,
  val category: String,
  val quantity: Long,
  val isAlwaysBring: Boolean,
  val contextReason: String,
  val sourceType: String,
)

data class DestinationPlace(
  val name: String,
  val country: String,
  val admin1: String,
  val latitude: Double,
  val longitude: Double,
) {
  val displayLabel: String
    get() = listOf(name, admin1, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
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
  val dailyPoints: List<DailyForecastPoint>,
  val recommendedGear: List<DraftPackingItem>,
)

data class CalendarEventInfo(
  val id: String,
  val title: String,
  val startEpochMillis: Long,
  val endEpochMillis: Long,
  val location: String,
  val sourceApp: String,
  val suggestedItems: List<DraftPackingItem> = emptyList(),
)

data class GoogleTaskListInfo(
  val id: String,
  val title: String,
)

data class GoogleTaskItemInfo(
  val id: String,
  val title: String,
  val notes: String,
  val isCompleted: Boolean,
)
