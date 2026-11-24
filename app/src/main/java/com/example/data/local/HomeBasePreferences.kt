package com.example.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

private val Context.homeBaseDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "packwise_home_base",
)

enum class HomeCountrySource(val label: String) {
  DEVICE_REGION("Device region"),
  LOCATION("Approximate location"),
  MANUAL("Set manually"),
}

data class HomeCountryPreference(
  val countryCode: String,
  val countryName: String,
  val source: HomeCountrySource,
)

class HomeBasePreferences(private val context: Context) {

  private val appContext = context.applicationContext

  val homeCountryFlow: Flow<HomeCountryPreference> = appContext.homeBaseDataStore.data.map { prefs ->
    val savedCode = prefs[KEY_COUNTRY_CODE].orEmpty().trim().uppercase(Locale.US)
    val savedName = prefs[KEY_COUNTRY_NAME].orEmpty().trim()
    val savedSource = prefs[KEY_SOURCE]?.let { raw ->
      HomeCountrySource.entries.firstOrNull { it.name == raw }
    }
    if (savedCode.length == 2 && savedName.isNotBlank() && savedSource != null) {
      HomeCountryPreference(
        countryCode = savedCode,
        countryName = savedName,
        source = savedSource,
      )
    } else {
      detectDeviceHomeCountry()
    }
  }

  fun detectDeviceHomeCountry(): HomeCountryPreference {
    val telephony = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val networkIso = telephony?.networkCountryIso?.trim()?.uppercase(Locale.US).orEmpty()
    val simIso = telephony?.simCountryIso?.trim()?.uppercase(Locale.US).orEmpty()
    val configLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      appContext.resources.configuration.locales[0]
    } else {
      @Suppress("DEPRECATION")
      appContext.resources.configuration.locale
    }
    val localeIso = (configLocale?.country ?: Locale.getDefault().country).trim().uppercase(Locale.US)

    val iso = listOf(networkIso, simIso, localeIso, "US")
      .firstOrNull { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
      ?: "US"
    val display = displayNameForIso(iso)
    return HomeCountryPreference(
      countryCode = iso,
      countryName = display,
      source = HomeCountrySource.DEVICE_REGION,
    )
  }

  suspend fun saveHomeCountry(
    countryCode: String,
    countryName: String,
    source: HomeCountrySource,
  ) {
    val cleanCode = countryCode.trim().uppercase(Locale.US).ifBlank { "US" }
    val cleanName = countryName.trim().ifBlank { displayNameForIso(cleanCode) }
    appContext.homeBaseDataStore.edit { prefs ->
      prefs[KEY_COUNTRY_CODE] = cleanCode
      prefs[KEY_COUNTRY_NAME] = cleanName
      prefs[KEY_SOURCE] = source.name
    }
  }

  suspend fun resetToDeviceRegion(): HomeCountryPreference {
    val detected = detectDeviceHomeCountry()
    appContext.homeBaseDataStore.edit { prefs ->
      prefs.remove(KEY_COUNTRY_CODE)
      prefs.remove(KEY_COUNTRY_NAME)
      prefs.remove(KEY_SOURCE)
    }
    return detected
  }

  fun hasCoarseLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
      appContext,
      Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

  suspend fun detectCountryFromCoarseLocation(): Result<HomeCountryPreference> {
    if (!hasCoarseLocationPermission()) {
      return Result.failure(IllegalStateException("Approximate location permission is required."))
    }

    val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return Result.failure(IllegalStateException("Location service is unavailable on this device."))

    val location = withContext(Dispatchers.Main) {
      getBestCoarseLocation(locationManager)
    } ?: return Result.failure(
      IllegalStateException("Could not obtain an approximate location fix right now. You can pick your home country manually below.")
    )

    val address = reverseGeocodeCountry(location.latitude, location.longitude)
      ?: return Result.failure(
        IllegalStateException("Could not determine country from location. Please choose your home country manually.")
      )

    val iso = address.countryCode?.trim()?.uppercase(Locale.US).orEmpty()
    val name = address.countryName?.trim().orEmpty()
    val resolvedCode = if (iso.length == 2) iso else resolveIsoCode(name).orEmpty()
    if (resolvedCode.isBlank()) {
      return Result.failure(IllegalStateException("Detected location did not include a recognizable country."))
    }
    val pref = HomeCountryPreference(
      countryCode = resolvedCode,
      countryName = name.ifBlank { displayNameForIso(resolvedCode) },
      source = HomeCountrySource.LOCATION,
    )
    saveHomeCountry(pref.countryCode, pref.countryName, HomeCountrySource.LOCATION)
    return Result.success(pref)
  }

  @Suppress("MissingPermission")
  private suspend fun getBestCoarseLocation(locationManager: LocationManager): Location? {
    val providers = listOf(
      LocationManager.NETWORK_PROVIDER,
      LocationManager.PASSIVE_PROVIDER,
      LocationManager.GPS_PROVIDER,
    )
    val lastKnown = providers.mapNotNull { provider ->
      runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }

    if (lastKnown != null) return lastKnown

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val enabledProvider = providers.firstOrNull { provider ->
        runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
      } ?: return null

      return withTimeoutOrNull(6_000L) {
        suspendCancellableCoroutine { cont ->
          val signal = CancellationSignal()
          cont.invokeOnCancellation { signal.cancel() }
          runCatching {
            locationManager.getCurrentLocation(
              enabledProvider,
              signal,
              Executors.newSingleThreadExecutor(),
            ) { loc ->
              if (cont.isActive) cont.resume(loc)
            }
          }.onFailure {
            if (cont.isActive) cont.resume(null)
          }
        }
      }
    }
    return null
  }

  private suspend fun reverseGeocodeCountry(latitude: Double, longitude: Double): Address? =
    withContext(Dispatchers.IO) {
      if (!Geocoder.isPresent()) return@withContext null
      val geocoder = Geocoder(appContext, Locale.ENGLISH)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        withTimeoutOrNull(6_000L) {
          suspendCancellableCoroutine { cont ->
            runCatching {
              geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                if (cont.isActive) cont.resume(addresses.firstOrNull())
              }
            }.onFailure {
              if (cont.isActive) cont.resume(null)
            }
          }
        }
      } else {
        @Suppress("DEPRECATION")
        runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
      }
    }

  companion object {
    private val KEY_COUNTRY_CODE = stringPreferencesKey("home_country_code")
    private val KEY_COUNTRY_NAME = stringPreferencesKey("home_country_name")
    private val KEY_SOURCE = stringPreferencesKey("home_country_source")

    private val COUNTRY_ALIASES: Map<String, String> = mapOf(
      "us" to "US",
      "usa" to "US",
      "u.s." to "US",
      "u.s.a." to "US",
      "united states" to "US",
      "united states of america" to "US",
      "uk" to "GB",
      "u.k." to "GB",
      "great britain" to "GB",
      "england" to "GB",
      "scotland" to "GB",
      "wales" to "GB",
      "northern ireland" to "GB",
      "united kingdom" to "GB",
      "south korea" to "KR",
      "republic of korea" to "KR",
      "uae" to "AE",
      "united arab emirates" to "AE",
      "czech republic" to "CZ",
      "czechia" to "CZ",
      "taiwan" to "TW",
      "vietnam" to "VN",
      "viet nam" to "VN",
      "russia" to "RU",
      "turkiye" to "TR",
      "turkey" to "TR",
      "netherlands" to "NL",
      "the netherlands" to "NL",
      "bahamas" to "BS",
      "the bahamas" to "BS",
      "philippines" to "PH",
      "the philippines" to "PH",
    )

    private val ISO_BY_LOWER_NAME: Map<String, String> by lazy {
      buildMap {
        Locale.getISOCountries().forEach { code ->
          val upper = code.uppercase(Locale.US)
          put(code.lowercase(Locale.ROOT), upper)
          val english = Locale("", upper).getDisplayCountry(Locale.ENGLISH).trim().lowercase(Locale.ROOT)
          if (english.isNotBlank()) put(english, upper)
          val defaultName = Locale("", upper).displayCountry.trim().lowercase(Locale.ROOT)
          if (defaultName.isNotBlank()) put(defaultName, upper)
        }
        putAll(COUNTRY_ALIASES)
      }
    }

    fun displayNameForIso(isoCode: String): String {
      val upper = isoCode.trim().uppercase(Locale.US)
      val name = Locale("", upper).getDisplayCountry(Locale.ENGLISH).trim()
      return name.ifBlank { upper }
    }

    fun resolveIsoCode(countryOrCode: String): String? {
      val normalized = countryOrCode.trim().lowercase(Locale.ROOT)
      if (normalized.isBlank()) return null
      return ISO_BY_LOWER_NAME[normalized]
    }

    fun allCountries(): List<Pair<String, String>> =
      Locale.getISOCountries()
        .map { code -> code.uppercase(Locale.US) to displayNameForIso(code) }
        .filter { it.second.isNotBlank() }
        .sortedBy { it.second }

    /**
     * Returns true when the trip destination is definitely in a different country from [home],
     * false when it's clearly domestic, or null if the trip country is unknown.
     */
    fun isTripInternational(
      home: HomeCountryPreference,
      tripCountry: String,
    ): Boolean? {
      val cleanTripCountry = tripCountry.trim()
      if (cleanTripCountry.isBlank()) return null

      val homeCode = home.countryCode.trim().uppercase(Locale.US).ifBlank {
        resolveIsoCode(home.countryName).orEmpty()
      }
      val tripCode = resolveIsoCode(cleanTripCountry)
      if (homeCode.isNotBlank() && tripCode != null) {
        return homeCode != tripCode
      }
      if (home.countryName.isNotBlank()) {
        return !cleanTripCountry.equals(home.countryName.trim(), ignoreCase = true)
      }
      return null
    }
  }
}
