package com.example.data.remote

import com.example.data.model.DailyForecastPoint
import com.example.data.model.DestinationPlace
import com.example.data.model.DraftPackingItem
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
  val time: List<String> = emptyList(),
  @Json(name = "weather_code") val weatherCode: List<Int> = emptyList(),
  @Json(name = "temperature_2m_max") val temperatureMax: List<Double> = emptyList(),
  @Json(name = "temperature_2m_min") val temperatureMin: List<Double> = emptyList(),
  @Json(name = "precipitation_probability_max") val precipitationProbMax: List<Int?> = emptyList(),
  @Json(name = "wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
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
    @Query("forecast_days") forecastDays: Int = 14,
  ): OpenMeteoForecastResponse
}

class WeatherRepository {
  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .addInterceptor(
      HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    )
    .build()

  private val geocodingApi: OpenMeteoGeocodingApi = Retrofit.Builder()
    .baseUrl("https://geocoding-api.open-meteo.com/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(OpenMeteoGeocodingApi::class.java)

  private val forecastApi: OpenMeteoForecastApi = Retrofit.Builder()
    .baseUrl("https://api.open-meteo.com/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(OpenMeteoForecastApi::class.java)

  suspend fun searchDestinations(query: String): Result<List<DestinationPlace>> = runCatching {
    if (query.trim().length < 2) return@runCatching emptyList()
    val response = geocodingApi.searchPlaces(query.trim())
    response.results.orEmpty().map { dto ->
      DestinationPlace(
        name = dto.name,
        country = dto.country.orEmpty(),
        admin1 = dto.admin1.orEmpty(),
        latitude = dto.latitude,
        longitude = dto.longitude,
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
    val daily = response.daily ?: throw IllegalStateException("No daily weather data returned")
    val startDate = LocalDate.ofEpochDay(startEpochDay)
    val endDate = LocalDate.ofEpochDay(endEpochDay)

    val allPoints = daily.time.indices.mapNotNull { idx ->
      val dateStr = daily.time.getOrNull(idx) ?: return@mapNotNull null
      val minT = daily.temperatureMin.getOrNull(idx) ?: 15.0
      val maxT = daily.temperatureMax.getOrNull(idx) ?: 22.0
      val rain = daily.precipitationProbMax.getOrNull(idx) ?: 0
      val wind = daily.windSpeedMax.getOrNull(idx) ?: 10.0
      val code = daily.weatherCode.getOrNull(idx) ?: 0
      DailyForecastPoint(
        dateIso = dateStr,
        tempMinC = minT,
        tempMaxC = maxT,
        rainProbPct = rain,
        windMaxKmh = wind,
        weatherCode = code,
        conditionLabel = mapWmoCode(code),
      )
    }

    val tripPoints = allPoints.filter { point ->
      val d = runCatching { LocalDate.parse(point.dateIso) }.getOrNull() ?: return@filter false
      !d.isBefore(startDate) && !d.isAfter(endDate)
    }.ifEmpty { allPoints.take(7) }

    if (tripPoints.isEmpty()) {
      throw IllegalStateException("Forecast unavailable for destination")
    }

    val minC = tripPoints.minOf { it.tempMinC }
    val maxC = tripPoints.maxOf { it.tempMaxC }
    val maxRain = tripPoints.maxOf { it.rainProbPct }.toLong()
    val maxWind = tripPoints.maxOf { it.windMaxKmh }
    val dominantCondition = tripPoints
      .groupingBy { it.conditionLabel }
      .eachCount()
      .maxByOrNull { it.value }
      ?.key ?: "Clear"

    val alerts = mutableListOf<String>()
    val recommendedGear = mutableListOf<DraftPackingItem>()

    if (maxRain >= 40L) {
      alerts.add("$maxRain% rain risk — pack waterproof jacket & compact umbrella")
      recommendedGear.add(
        DraftPackingItem(
          name = "Waterproof Rain Jacket",
          category = "Outerwear",
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = "$maxRain% rain chance in live forecast",
          sourceType = "weather",
        )
      )
      recommendedGear.add(
        DraftPackingItem(
          name = "Compact Travel Umbrella",
          category = "Weather Gear",
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = "$maxRain% rain chance in live forecast",
          sourceType = "weather",
        )
      )
    }
    if (minC <= 10.0) {
      alerts.add("Cold temps (${minC.roundToInt()}°C) — bring thermal layers & coat")
      recommendedGear.add(
        DraftPackingItem(
          name = "Insulated Coat & Thermal Layer",
          category = "Outerwear",
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = "Low of ${minC.roundToInt()}°C forecasted",
          sourceType = "weather",
        )
      )
    }
    if (maxC >= 28.0) {
      alerts.add("Warm sun (${maxC.roundToInt()}°C) — pack sunscreen & UV sunglasses")
      recommendedGear.add(
        DraftPackingItem(
          name = "SPF 50 Sunscreen & Polarized Sunglasses",
          category = "Weather Gear",
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = "High of ${maxC.roundToInt()}°C forecasted",
          sourceType = "weather",
        )
      )
    }
    if (maxWind >= 35.0) {
      alerts.add("Gusty winds (${maxWind.roundToInt()} km/h) — pack windbreaker")
      recommendedGear.add(
        DraftPackingItem(
          name = "Packable Windbreaker",
          category = "Outerwear",
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = "Winds up to ${maxWind.roundToInt()} km/h",
          sourceType = "weather",
        )
      )
    }

    val summaryText = "${minC.roundToInt()}°C–${maxC.roundToInt()}°C · $maxRain% rain · $dominantCondition"
    val alertMessage = alerts.firstOrNull() ?: ""

    WeatherForecastSummary(
      summaryText = summaryText,
      tempMinC = minC,
      tempMaxC = maxC,
      rainProbMax = maxRain,
      maxWindKmh = maxWind,
      alertActive = alerts.isNotEmpty(),
      alertMessage = alertMessage,
      dailyPoints = tripPoints,
      recommendedGear = recommendedGear,
    )
  }

  private fun mapWmoCode(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2, 3 -> "Partly cloudy"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorms"
    else -> "Variable weather"
  }
}
