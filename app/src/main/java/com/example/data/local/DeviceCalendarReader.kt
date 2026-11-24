package com.example.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.CalendarEventInfo
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DeviceCalendarReader(private val context: Context) {

  fun hasCalendarPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun queryEventsForDateRange(
    startEpochDay: Long,
    endEpochDay: Long,
  ): List<CalendarEventInfo> {
    if (!hasCalendarPermission()) return emptyList()

    val zone = ZoneId.systemDefault()
    val startMs = LocalDate.ofEpochDay(startEpochDay).atStartOfDay(zone).toInstant().toEpochMilli()
    val endMs = LocalDate.ofEpochDay(endEpochDay).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
      android.content.ContentUris.appendId(this, startMs)
      android.content.ContentUris.appendId(this, endMs)
    }.build()

    val projection = arrayOf(
      CalendarContract.Instances.EVENT_ID,
      CalendarContract.Instances.TITLE,
      CalendarContract.Instances.BEGIN,
      CalendarContract.Instances.END,
      CalendarContract.Instances.EVENT_LOCATION,
      CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
    )

    val results = mutableListOf<CalendarEventInfo>()
    try {
      context.contentResolver.query(
        uri,
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
      )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
        val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
        val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
        val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
        val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
        val calIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

        val seen = mutableSetOf<String>()
        while (cursor.moveToNext() && results.size < 30) {
          val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty().trim() else ""
          if (title.isBlank()) continue
          val eventId = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else title
          val start = if (beginIdx >= 0) cursor.getLong(beginIdx) else startMs
          val end = if (endIdx >= 0) cursor.getLong(endIdx) else start
          val loc = if (locIdx >= 0) cursor.getString(locIdx).orEmpty().trim() else ""
          val sourceApp = if (calIdx >= 0) cursor.getString(calIdx).orEmpty().ifBlank { "Device Calendar" } else "Device Calendar"

          val dedupKey = "${eventId}_${start}"
          if (seen.add(dedupKey)) {
            results.add(
              CalendarEventInfo(
                id = dedupKey,
                title = title,
                startEpochMillis = start,
                endEpochMillis = end,
                location = loc,
                sourceApp = sourceApp,
                suggestedItems = suggestItemsForEvent(title, loc),
              )
            )
          }
        }
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "Calendar permission denied while reading instances", e)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to query device calendar instances", e)
    }
    return results
  }

  companion object {
    private const val TAG = "DeviceCalendarReader"

    fun suggestItemsForEvent(title: String, location: String): List<String> {
      val text = "$title $location".lowercase(Locale.ROOT)
      val gear = linkedSetOf<String>()

      if (text.containsAny("flight", "airport", "boarding", "train", "eurostar", "ferry")) {
        gear += listOf("Boarding Pass & Travel Wallet", "Neck Pillow & Earplugs")
      }
      if (text.containsAny("meeting", "conference", "keynote", "office", "client", "interview", "summit")) {
        gear += listOf("Blazer / Business Attire", "Laptop & Presenter Adapter", "Dress Shoes")
      }
      if (text.containsAny("dinner", "gala", "wedding", "cocktail", "opera", "theater", "date", "reservation")) {
        gear += listOf("Evening Outfit", "Smart Footwear")
      }
      if (text.containsAny("hike", "trail", "mountain", "trek", "camp", "park", "climb")) {
        gear += listOf("Broken-in Hiking Shoes", "Daypack & Trail Snacks")
      }
      if (text.containsAny("swim", "beach", "pool", "spa", "Surf", "kayak", "snorkel", "resort")) {
        gear += listOf("Swimwear", "Quick-Dry Towel & Sandals")
      }
      if (text.containsAny("gym", "run", "marathon", "yoga", "workout", "fitness")) {
        gear += listOf("Workout Clothes & Running Shoes")
      }
      if (text.containsAny("museum", "tour", "walk", "city", "exhibit")) {
        gear += listOf("Comfortable Walking Shoes", "Portable Battery Pack")
      }
      if (gear.isEmpty()) {
        gear += "Day Bag & Comfortable Outfit"
      }
      return gear.toList()
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
      keywords.any { this.contains(it, ignoreCase = true) }
  }
}
