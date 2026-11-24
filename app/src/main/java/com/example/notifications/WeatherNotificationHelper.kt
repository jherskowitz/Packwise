package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object WeatherNotificationHelper {
  const val CHANNEL_ID = "weather_packing_alerts"
  const val EXTRA_TRIP_ID = "extra_trip_id"

  fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Weather Packing Alerts",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Real-time weather changes and gear packing reminders for upcoming trips"
      }
      manager?.createNotificationChannel(channel)
    }
  }

  fun showWeatherPackingAlert(
    context: Context,
    tripId: String,
    destination: String,
    message: String,
  ) {
    if (!hasNotificationPermission(context)) return
    ensureChannel(context)

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_TRIP_ID, tripId)
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      tripId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.packwise_icon_fg)
      .setContentTitle("Weather Packing Alert · $destination")
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    NotificationManagerCompat.from(context).notify(tripId.hashCode(), notification)
  }
}
