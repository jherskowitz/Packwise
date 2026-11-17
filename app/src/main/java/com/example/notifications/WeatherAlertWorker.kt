package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.firebase.PackingRepository
import com.example.data.remote.WeatherRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit

class WeatherAlertWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val user = Firebase.auth.currentUser ?: return Result.success()
    val repo = PackingRepository(applicationContext)
    val weatherRepo = WeatherRepository()

    return try {
      val activeTrips = repo.getActiveTripsOnce()
        .filter { it.weatherNotificationsEnabled && it.userId == user.uid }

      for (trip in activeTrips) {
        val forecast = weatherRepo.fetchForecastForTrip(
          latitude = trip.latitude,
          longitude = trip.longitude,
          startEpochDay = trip.startDateEpochDay,
          endEpochDay = trip.endDateEpochDay,
        ).getOrNull() ?: continue

        repo.updateTripWeather(trip.id, forecast)

        if (forecast.alertActive && forecast.alertMessage.isNotBlank()) {
          val items = runCatching { repo.getTripItemsOnce(trip.id) }.getOrDefault(emptyList())
          val unpackedCount = items.count { !it.isPacked }
          val body = if (unpackedCount > 0) {
            "${forecast.alertMessage}. $unpackedCount items still unpacked."
          } else {
            forecast.alertMessage
          }
          WeatherNotificationHelper.postWeatherAlertNotification(
            context = applicationContext,
            tripId = trip.id,
            destination = trip.destination,
            title = "Weather Packing Alert",
            message = body,
          )
        }
      }
      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "WeatherAlertWorker failed", e)
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "WeatherAlertWorker"
    private const val PERIODIC_WORK_NAME = "packwise_weather_alert_periodic"

    fun schedulePeriodic(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }

    fun triggerImmediateCheck(context: Context) {
      val request = OneTimeWorkRequestBuilder<WeatherAlertWorker>().build()
      WorkManager.getInstance(context).enqueue(request)
    }
  }
}
