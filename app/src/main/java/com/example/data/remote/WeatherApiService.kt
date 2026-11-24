package com.example.data.remote

import com.example.data.model.DailyForecastPoint
import com.example.data.model.DestinationPlace
import com.example.data.model.WeatherForecastSummary
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
  val results: List<GeocodingResultDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
  val name: String = "",
  val country: String? = null,
  val admin1: String? = null,
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class OpenMeteoForecastResponse(
  val daily: DailyWeatherDto? = null,
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
  val time: List<String>? = null,
  @Json(name = "weather_code") val weatherCode: List<Int>? = null,
  @Json(name = "temperature_2m_max") val tempMax: List<Double>? = null,
  @Json(name = "temperature_2m_min") val tempMin: List<Double>? = null,
  @Json(name = "precipitation_probability_max") val precipProbMax: List<Int>? = null,
  @Json(name = "wind_speed_10m_max") val windSpeedMax: List<Double>? = null,
)

interface OpenMeteoGeocodingApi {
  @GET("v1/search")
  suspend fun searchPlaces(
    @Query("name") query: String,
    @Query("count") count: Int = 6,
    @Query("language") language: String = "en",
    @Query("format") format: String = "json",
  ): GeocodingResponse
}

interface OpenMeteoForecastApi {
  @GET("v1/forecast")
  suspend fun getDailyForecast(
    @Query("latitude") latitude: Double,
    @Query("longitude") longitude: Double,
    @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max",
    @Query("timezone") timezone: String = "auto",
    @Query("forecast_days") forecastDays: Int = 10,
  ): OpenMeteoForecastResponse
}

class WeatherRepository {
  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .writeTimeout(8, TimeUnit.SECONDS)
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    .build()

  private val geocodingApi: OpenMeteoGeocodingApi = Retrofit.Builder()
    .baseUrl("https://geocoding-api.open-meteo.com/")
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(OpenMeteoGeocodingApi::class.java)

  private val forecastApi: OpenMeteoForecastApi = Retrofit.Builder()
    .baseUrl("https://api.open-meteo.com/")
    .client(client)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(OpenMeteoForecastApi::class.java)

  suspend fun searchDestinations(query: String): Result<List<DestinationPlace>> = runCatching {
    val trimmed = query.trim()
    if (trimmed.length < 2) return@runCatching emptyList()
    val res = geocodingApi.searchPlaces(trimmed)
    res.results.orEmpty()
      .filter { it.name.isNotBlank() }
      .map {
        DestinationPlace(
          name = it.name,
          country = it.country.orEmpty(),
          admin1 = it.admin1.orEmpty(),
          latitude = it.latitude,
          longitude = it.longitude,
        )
      }
  }

  suspend fun fetchForecastForTrip(
    latitude: Double,
    longitude: Double,
    startEpochDay: Long,
    endEpochDay: Long,
  ): Result<WeatherForecastSummary> = runCatching {
    val response = forecastApi.getDailyForecast(latitude = latitude, longitude = longitude)
    val daily = response.daily ?: throw IllegalStateException("Forecast unavailable for destination")
    val dates = daily.time.orEmpty()
    if (dates.isEmpty()) throw IllegalStateException("No daily weather data returned")

    val startIso = LocalDate.ofEpochDay(startEpochDay).toString()
    val endIso = LocalDate.ofEpochDay(endEpochDay).toString()

    val allPoints = dates.indices.map { i ->
      val code = daily.weatherCode?.getOrNull(i) ?: 0
      DailyForecastPoint(
        dateIso = dates[i],
        tempMinC = daily.tempMin?.getOrNull(i) ?: 14.0,
        tempMaxC = daily.tempMax?.getOrNull(i) ?: 22.0,
        rainProbPct = daily.precipProbMax?.getOrNull(i) ?: 0,
        windMaxKmh = daily.windSpeedMax?.getOrNull(i) ?: 10.0,
        weatherCode = code,
        conditionLabel = weatherCodeToLabel(code),
      )
    }

    val matchingPoints = allPoints.filter { it.dateIso in startIso..endIso }.ifEmpty { allPoints }
    val minC = matchingPoints.minOf { it.tempMinC }
    val maxC = matchingPoints.maxOf { it.tempMaxC }
    val maxRain = matchingPoints.maxOf { it.rainProbPct }.toLong()
    val maxWind = matchingPoints.maxOf { it.windMaxKmh }
    val primaryCondition = matchingPoints
      .groupingBy { it.conditionLabel }
      .eachCount()
      .maxByOrNull { it.value }
      ?.key ?: "Variable weather"

    val alertReasons = mutableListOf<String>()
    val recommendedGear = mutableListOf<String>()

    if (maxRain >= 45L) {
      alertReasons += "$maxRain% rain risk — pack waterproof jacket & compact umbrella"
      recommendedGear += listOf("Waterproof Rain Jacket", "Compact Travel Umbrella")
    }
    if (minC <= 8.0) {
      alertReasons += "Cold temps (${minC.roundToInt()}°C) — bring thermal layers & coat"
      recommendedGear += "Insulated Coat & Thermal Layer"
    }
    if (maxC >= 29.0) {
      alertReasons += "Warm sun (${maxC.roundToInt()}°C) — pack sunscreen & UV sunglasses"
      recommendedGear += "SPF 50 Sunscreen & Polarized Sunglasses"
    }
    if (maxWind >= 35.0) {
      alertReasons += "Gusty winds (${maxWind.roundToInt()} km/h) — pack windbreaker"
      recommendedGear += "Packable Windbreaker"
    }

    val summaryText = String.format(
      Locale.US,
      "%s · %.0f°C–%.0f°C · %d%% rain · Winds up to %.0f km/h",
      primaryCondition,
      minC,
      maxC,
      maxRain,
      maxWind,
    )

    WeatherForecastSummary(
      summaryText = summaryText,
      tempMinC = minC,
      tempMaxC = maxC,
      rainProbMax = maxRain,
      maxWindKmh = maxWind,
      alertActive = alertReasons.isNotEmpty(),
      alertMessage = alertReasons.joinToString(". "),
      recommendedGear = recommendedGear.distinct(),
      dailyPoints = matchingPoints,
    )
  }

  companion object {
    fun weatherCodeToLabel(code: Int): String = when (code) {
      0 -> "Clear sky"
      1, 2, 3 -> "Partly cloudy"
      45, 48 -> "Foggy"
      51, 53, 55, 56, 57 -> "Drizzle"
      61, 63, 65, 66, 67 -> "Rain"
      71, 73, 75, 77 -> "Snow"
      80, 81, 82 -> "Rain showers"
      85, 86 -> "Snow showers"
      95, 96, 99 -> "Thunderstorms"
      else -> "Clear"
    }
  }
}
