package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.firebase.PackingRepository
import com.example.data.remote.WeatherRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class WeatherAlertWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val repo = PackingRepository(applicationContext)
      val weatherRepo = WeatherRepository()
      val today = LocalDate.now().toEpochDay()
      val activeTrips = repo.getActiveTripsOnce()
        .filter { it.weatherNotificationsEnabled && it.endDateEpochDay >= today && (it.latitude != 0.0 || it.longitude != 0.0) }

      activeTrips.forEach { trip ->
        weatherRepo.fetchForecastForTrip(
          latitude = trip.latitude,
          longitude = trip.longitude,
          startEpochDay = trip.startDateEpochDay,
          endEpochDay = trip.endDateEpochDay,
        ).onSuccess { forecast ->
          repo.updateTripWeather(trip.id, forecast)
          if (forecast.alertActive && forecast.alertMessage.isNotBlank()) {
            WeatherNotificationHelper.showWeatherPackingAlert(
              context = applicationContext,
              tripId = trip.id,
              destination = trip.destination,
              message = forecast.alertMessage,
            )
          }
        }
      }
      Result.success()
    } catch (e: Exception) {
      Log.w(TAG, "Background weather check encountered error", e)
      Result.retry()
    }
  }

  companion object {
    private const val TAG = "WeatherAlertWorker"
    private const val PERIODIC_WORK_NAME = "packwise_periodic_weather_check"
    private const val IMMEDIATE_WORK_NAME = "packwise_immediate_weather_check"

    fun schedulePeriodic(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
      val req = PeriodicWorkRequestBuilder<WeatherAlertWorker>(6, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        req,
      )
    }

    fun triggerImmediateCheck(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
      val req = OneTimeWorkRequestBuilder<WeatherAlertWorker>()
        .setConstraints(constraints)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(
        IMMEDIATE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        req,
      )
    }
  }
}
