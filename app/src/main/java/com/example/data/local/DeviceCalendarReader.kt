package com.example.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.model.CalendarEventInfo
import com.example.data.model.DraftPackingItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DeviceCalendarReader(private val context: Context) {

  fun hasCalendarPermission(): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
      PackageManager.PERMISSION_GRANTED

  fun readEventsForRange(startEpochDay: Long, endEpochDay: Long): List<CalendarEventInfo> {
    if (!hasCalendarPermission()) return emptyList()

    val zone = ZoneId.systemDefault()
    val startMillis = LocalDate.ofEpochDay(startEpochDay)
      .atStartOfDay(zone)
      .toInstant()
      .toEpochMilli()
    val endMillis = LocalDate.ofEpochDay(endEpochDay)
      .plusDays(1)
      .atStartOfDay(zone)
      .toInstant()
      .toEpochMilli()

    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(builder, startMillis)
    ContentUris.appendId(builder, endMillis)

    val projection = arrayOf(
      CalendarContract.Instances.EVENT_ID,
      CalendarContract.Instances.TITLE,
      CalendarContract.Instances.BEGIN,
      CalendarContract.Instances.END,
      CalendarContract.Instances.EVENT_LOCATION,
      CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
    )

    val events = mutableListOf<CalendarEventInfo>()
    runCatching {
      context.contentResolver.query(
        builder.build(),
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

        while (cursor.moveToNext() && events.size < 25) {
          val id = if (idIdx >= 0) cursor.getLong(idIdx).toString() else ""
          val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty().trim() else ""
          val begin = if (beginIdx >= 0) cursor.getLong(beginIdx) else startMillis
          val end = if (endIdx >= 0) cursor.getLong(endIdx) else begin
          val location = if (locIdx >= 0) cursor.getString(locIdx).orEmpty().trim() else ""
          val calName = if (calIdx >= 0) cursor.getString(calIdx).orEmpty().trim() else "Calendar"

          if (title.isNotBlank()) {
            events.add(
              CalendarEventInfo(
                id = id,
                title = title,
                startEpochMillis = begin,
                endEpochMillis = end,
                location = location,
                sourceApp = calName.ifBlank { "Device Calendar" },
                suggestedItems = suggestItemsForEvent(title, location),
              )
            )
          }
        }
      }
    }
    return events
  }

  companion object {
    fun formatEventSummary(event: CalendarEventInfo): String {
      val date = Instant.ofEpochMilli(event.startEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
      return "${event.title} ($date)"
    }

    fun suggestItemsForEvent(title: String, location: String = ""): List<DraftPackingItem> {
      val text = "$title $location".lowercase()
      val suggestions = mutableListOf<DraftPackingItem>()

      if (listOf("dinner", "gala", "wedding", "formal", "conference", "keynote", "client", "meeting", "interview").any { text.contains(it) }) {
        suggestions.add(
          DraftPackingItem(
            name = "Smart / Formal Outfit & Dress Shoes",
            category = "Clothing",
            quantity = 1L,
            isAlwaysBring = false,
            contextReason = "Calendar: $title",
            sourceType = "calendar",
          )
        )
      }
      if (listOf("presentation", "pitch", "workshop", "work", "office", "meeting", "sync").any { text.contains(it) }) {
        suggestions.add(
          DraftPackingItem(
            name = "Laptop & Presentation Adapter / Dongle",
            category = "Tech",
            quantity = 1L,
            isAlwaysBring = false,
            contextReason = "Calendar: $title",
            sourceType = "calendar",
          )
        )
      }
      if (listOf("hike", "trail", "mountain", "trek", "climb", "park").any { text.contains(it) }) {
        suggestions.add(
          DraftPackingItem(
            name = "Trail Shoes & Daypack",
            category = "Activity Gear",
            quantity = 1L,
            isAlwaysBring = false,
            contextReason = "Calendar: $title",
            sourceType = "calendar",
          )
        )
      }
      if (listOf("swim", "pool", "beach", "spa", "kayak", "surf", "hot spring").any { text.contains(it) }) {
        suggestions.add(
          DraftPackingItem(
            name = "Swimwear & Quick-Dry Towel",
            category = "Activity Gear",
            quantity = 1L,
            isAlwaysBring = false,
            contextReason = "Calendar: $title",
            sourceType = "calendar",
          )
        )
      }
      if (listOf("gym", "run", "yoga", "workout", "fitness", "tennis", "golf").any { text.contains(it) }) {
        suggestions.add(
          DraftPackingItem(
            name = "Athletic Wear & Running Sneakers",
            category = "Clothing",
            quantity = 1L,
            isAlwaysBring = false,
            contextReason = "Calendar: $title",
            sourceType = "calendar",
          )
        )
      }
      if (listOf("flight", "airport", "train", "boarding", "check-in", "hotel").any { text.contains(it) }) {
        suggestions.add(
          DraftPackingItem(
            name = "Travel Neck Pillow & Boarding Docs",
            category = "Documents",
            quantity = 1L,
            isAlwaysBring = false,
            contextReason = "Calendar: $title",
            sourceType = "calendar",
          )
        )
      }
      return suggestions
    }
  }
}
