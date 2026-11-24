package com.example.ui.viewmodel

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiChecklistGenerator
import com.example.data.ai.NanoAvailabilityStatus
import com.example.data.firebase.PackingRepository
import com.example.data.local.CategoryPreferences
import com.example.data.local.DeviceCalendarReader
import com.example.data.local.HomeBasePreferences
import com.example.data.local.HomeCountryPreference
import com.example.data.local.HomeCountrySource
import com.example.data.model.AiItemRecommendation
import com.example.data.model.AlwaysBringItem
import com.example.data.model.AttractionSuggestion
import com.example.data.model.CATEGORY_ALWAYS_DO
import com.example.data.model.CalendarEventInfo
import com.example.data.model.DestinationPlace
import com.example.data.model.DraftPackingItem
import com.example.data.model.GoogleTaskItemInfo
import com.example.data.model.GoogleTaskListInfo
import com.example.data.model.PackingItem
import com.example.data.model.SOURCE_ALWAYS_BRING
import com.example.data.model.SOURCE_ALWAYS_DO
import com.example.data.model.TasksImportTarget
import com.example.data.model.Trip
import com.example.data.model.TripItemScope
import com.example.data.model.TripSortOrder
import com.example.data.model.UiState
import com.example.data.model.VaultItemType
import com.example.data.model.WeatherForecastSummary
import com.example.data.model.sortedByOrder
import com.example.data.remote.DestinationPhotoRepository
import com.example.data.remote.GoogleWorkspaceRepository
import com.example.data.remote.WeatherRepository
import com.example.data.remote.WorkspaceAuthState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class GoogleWorkspacePendingAction {
  TASKS,
  CALENDAR,
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PackWiseViewModel(
  private val repository: PackingRepository,
  private val weatherRepository: WeatherRepository,
  private val destinationPhotoRepository: DestinationPhotoRepository,
  private val aiChecklistGenerator: AiChecklistGenerator,
  private val workspaceRepository: GoogleWorkspaceRepository,
  private val deviceCalendarReader: DeviceCalendarReader,
  private val homeBasePreferences: HomeBasePreferences,
  private val categoryPreferences: CategoryPreferences,
  private val currentUserId: String,
) : ViewModel() {

  val homeCountry: StateFlow<HomeCountryPreference> = homeBasePreferences.homeCountryFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = homeBasePreferences.detectDeviceHomeCountry(),
    )

  val customCategories: StateFlow<List<String>> = categoryPreferences.customCategoriesFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList(),
    )

  val tripSortOrder: StateFlow<TripSortOrder> = homeBasePreferences.tripSortOrderFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = TripSortOrder.CHRONOLOGICAL,
    )

  val tripsState: StateFlow<UiState<List<Trip>>> = combine(
    repository.observeTrips(currentUserId),
    tripSortOrder,
  ) { trips, sortOrder ->
    UiState.Success(trips.sortedByOrder(sortOrder)) as UiState<List<Trip>>
  }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing trips", root)
      emit(UiState.Error(root.localizedMessage ?: "Failed to load trips."))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  val alwaysBringState: StateFlow<UiState<List<AlwaysBringItem>>> = repository.observeAlwaysBringItems(currentUserId)
    .map<List<AlwaysBringItem>, UiState<List<AlwaysBringItem>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing always bring items", root)
      emit(UiState.Error(root.localizedMessage ?: "Failed to load Always Bring vault."))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  private val _selectedTripId = MutableStateFlow<String?>(null)
  val selectedTripId: StateFlow<String?> = _selectedTripId.asStateFlow()

  val selectedTripItemsState: StateFlow<UiState<List<PackingItem>>> = _selectedTripId
    .flatMapLatest { tripId ->
      if (tripId.isNullOrBlank()) {
        flowOf<UiState<List<PackingItem>>>(UiState.Idle)
      } else {
        repository.observeTripItems(currentUserId, tripId)
          .map<List<PackingItem>, UiState<List<PackingItem>>> { UiState.Success(it) }
          .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing trip items", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load checklist."))
          }
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  private val _destinationQuery = MutableStateFlow("")
  val destinationQuery: StateFlow<String> = _destinationQuery.asStateFlow()

  private val _placeSuggestions = MutableStateFlow<List<DestinationPlace>>(emptyList())
  val placeSuggestions: StateFlow<List<DestinationPlace>> = _placeSuggestions.asStateFlow()

  private val _selectedPlace = MutableStateFlow<DestinationPlace?>(null)
  val selectedPlace: StateFlow<DestinationPlace?> = _selectedPlace.asStateFlow()

  private val _startEpochDay = MutableStateFlow(LocalDate.now().plusDays(1).toEpochDay())
  val startEpochDay: StateFlow<Long> = _startEpochDay.asStateFlow()

  private val _endEpochDay = MutableStateFlow(LocalDate.now().plusDays(5).toEpochDay())
  val endEpochDay: StateFlow<Long> = _endEpochDay.asStateFlow()

  private val _selectedActivities = MutableStateFlow(setOf("City Walking"))
  val selectedActivities: StateFlow<Set<String>> = _selectedActivities.asStateFlow()

  private val _weatherForecastState = MutableStateFlow<UiState<WeatherForecastSummary>>(UiState.Idle)
  val weatherForecastState: StateFlow<UiState<WeatherForecastSummary>> = _weatherForecastState.asStateFlow()

  private val _calendarEvents = MutableStateFlow<List<CalendarEventInfo>>(emptyList())
  val calendarEvents: StateFlow<List<CalendarEventInfo>> = _calendarEvents.asStateFlow()

  private val _calendarStatusMessage = MutableStateFlow<String?>(null)
  val calendarStatusMessage: StateFlow<String?> = _calendarStatusMessage.asStateFlow()

  private val _plannerGenerationState = MutableStateFlow<UiState<String>>(UiState.Idle)
  val plannerGenerationState: StateFlow<UiState<String>> = _plannerGenerationState.asStateFlow()

  private val _tripAiEnrichmentState = MutableStateFlow<Map<String, UiState<String>>>(emptyMap())
  val tripAiEnrichmentState: StateFlow<Map<String, UiState<String>>> = _tripAiEnrichmentState.asStateFlow()

  private val _tripAiRecommendationsState = MutableStateFlow<Map<String, UiState<AiItemRecommendation>>>(emptyMap())
  val tripAiRecommendationsState: StateFlow<Map<String, UiState<AiItemRecommendation>>> =
    _tripAiRecommendationsState.asStateFlow()

  private val _tripGuideLoadingState = MutableStateFlow<Map<String, UiState<Unit>>>(emptyMap())
  val tripGuideLoadingState: StateFlow<Map<String, UiState<Unit>>> = _tripGuideLoadingState.asStateFlow()

  private val _googleTaskListsState = MutableStateFlow<UiState<List<GoogleTaskListInfo>>>(UiState.Idle)
  val googleTaskListsState: StateFlow<UiState<List<GoogleTaskListInfo>>> = _googleTaskListsState.asStateFlow()

  private val _selectedTaskListId = MutableStateFlow<String?>(null)
  val selectedTaskListId: StateFlow<String?> = _selectedTaskListId.asStateFlow()

  private val _googleTasksItemsState = MutableStateFlow<UiState<List<GoogleTaskItemInfo>>>(UiState.Idle)
  val googleTasksItemsState: StateFlow<UiState<List<GoogleTaskItemInfo>>> = _googleTasksItemsState.asStateFlow()

  private val _googleTasksAccessToken = MutableStateFlow<String?>(null)

  private val _pendingConsentIntent = MutableStateFlow<Pair<GoogleWorkspacePendingAction, PendingIntent>?>(null)
  val pendingConsentIntent: StateFlow<Pair<GoogleWorkspacePendingAction, PendingIntent>?> =
    _pendingConsentIntent.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _nanoStatus = MutableStateFlow(NanoAvailabilityStatus.UNAVAILABLE)
  val nanoStatus: StateFlow<NanoAvailabilityStatus> = _nanoStatus.asStateFlow()

  private val _nanoDownloadedMb = MutableStateFlow<Long?>(null)
  val nanoDownloadedMb: StateFlow<Long?> = _nanoDownloadedMb.asStateFlow()

  @Volatile
  private var autoDownloadTriggered = false

  private val photoLookupAttempted = mutableSetOf<String>()
  private val regionLookupAttempted = mutableSetOf<String>()

  init {
    viewModelScope.launch {
      repository.seedDefaultAlwaysBringIfEmpty()
    }
    viewModelScope.launch {
      refreshNanoStatusInternal()
    }
    viewModelScope.launch {
      _destinationQuery
        .debounce(350L)
        .distinctUntilChanged()
        .collect { query ->
          val trimmed = query.trim()
          if (trimmed.length < 2 || _selectedPlace.value?.displayLabel() == query) {
            _placeSuggestions.value = emptyList()
          } else {
            weatherRepository.searchDestinations(trimmed)
              .onSuccess { _placeSuggestions.value = it }
              .onFailure { _placeSuggestions.value = emptyList() }
          }
        }
    }
  }

  fun selectTrip(tripId: String) {
    _selectedTripId.value = tripId
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  fun showToast(message: String) {
    _toastMessage.value = message
  }

  fun setTripSortOrder(sortOrder: TripSortOrder) {
    viewModelScope.launch {
      homeBasePreferences.saveTripSortOrder(sortOrder)
    }
  }

  fun setManualHomeCountry(countryCode: String, countryName: String) {
    viewModelScope.launch {
      homeBasePreferences.saveHomeCountry(countryCode, countryName, HomeCountrySource.MANUAL)
      _toastMessage.value = "Home country set to $countryName."
    }
  }

  fun resetHomeCountryToDeviceRegion() {
    viewModelScope.launch {
      val detected = homeBasePreferences.resetToDeviceRegion()
      _toastMessage.value = "Home country set to ${detected.countryName} (from device region)."
    }
  }

  fun detectHomeCountryFromLocation() {
    viewModelScope.launch {
      homeBasePreferences.detectCountryFromCoarseLocation()
        .onSuccess { pref ->
          _toastMessage.value = "Home country updated to ${pref.countryName} from approximate location."
        }
        .onFailure { err ->
          _toastMessage.value = err.localizedMessage ?: "Could not detect country from location."
        }
    }
  }

  fun ensureTripHeaderPhotos(trips: List<Trip>) {
    trips.forEach { trip ->
      if (trip.headerImageUrl.isBlank() && photoLookupAttempted.add(trip.id)) {
        viewModelScope.launch {
          val photoUrl = destinationPhotoRepository.fetchDestinationPhotoUrl(
            destination = trip.destination,
            country = trip.country,
          )
          if (!photoUrl.isNullOrBlank()) {
            repository.updateTripHeaderImage(trip.id, photoUrl)
          }
        }
      }
    }
  }

  fun ensureTripRegionLabels(trips: List<Trip>) {
    trips.forEach { trip ->
      if (
        trip.id.isNotBlank() &&
        trip.destination.isNotBlank() &&
        !trip.destination.contains(",") &&
        regionLookupAttempted.add(trip.id)
      ) {
        viewModelScope.launch {
          val candidates = weatherRepository.searchDestinations(trip.destination).getOrNull().orEmpty()
          if (candidates.isEmpty()) return@launch
          val hasCoords = trip.latitude != 0.0 || trip.longitude != 0.0
          val best = if (hasCoords) {
            candidates
              .filter { abs(it.latitude - trip.latitude) < 1.0 && abs(it.longitude - trip.longitude) < 1.0 }
              .minByOrNull { abs(it.latitude - trip.latitude) + abs(it.longitude - trip.longitude) }
              ?: candidates.firstOrNull {
                trip.country.isBlank() || it.country.equals(trip.country, ignoreCase = true)
              }
          } else {
            candidates.firstOrNull {
              trip.country.isBlank() || it.country.equals(trip.country, ignoreCase = true)
            }
          } ?: return@launch

          val updatedDest = best.cityAndRegionLabel()
          val updatedCountry = trip.country.ifBlank { best.country }
          if (updatedDest.isNotBlank() && updatedDest != trip.destination && updatedDest.contains(",")) {
            repository.updateTripDestination(trip.id, updatedDest, updatedCountry)
          }
        }
      }
    }
  }

  fun refreshNanoStatus() {
    viewModelScope.launch {
      refreshNanoStatusInternal()
    }
  }

  private suspend fun refreshNanoStatusInternal() {
    _nanoStatus.value = aiChecklistGenerator.checkNanoStatus()
  }

  fun prepareGeminiNano(userInitiated: Boolean = true) {
    if (_nanoStatus.value == NanoAvailabilityStatus.DOWNLOADING) {
      if (userInitiated) _toastMessage.value = "Gemini Nano is already downloading in the background."
      return
    }
    viewModelScope.launch {
      val current = aiChecklistGenerator.checkNanoStatus()
      _nanoStatus.value = current
      when (current) {
        NanoAvailabilityStatus.AVAILABLE -> {
          if (userInitiated) _toastMessage.value = "Gemini Nano is ready on this device."
        }
        NanoAvailabilityStatus.DOWNLOADING -> {
          if (userInitiated) _toastMessage.value = "Gemini Nano is currently downloading."
        }
        NanoAvailabilityStatus.UNAVAILABLE -> {
          if (userInitiated) {
            _toastMessage.value = "Gemini Nano isn't supported on this device; Cloud Gemini is active."
          }
        }
        NanoAvailabilityStatus.DOWNLOADABLE -> {
          _nanoStatus.value = NanoAvailabilityStatus.DOWNLOADING
          _nanoDownloadedMb.value = 0L
          if (userInitiated) {
            _toastMessage.value = "Downloading Gemini Nano for offline on-device packing lists..."
          }
          aiChecklistGenerator.downloadNanoModel { bytes ->
            _nanoDownloadedMb.value = bytes / (1024L * 1024L)
          }.onSuccess {
            _nanoDownloadedMb.value = null
            _nanoStatus.value = aiChecklistGenerator.checkNanoStatus()
            if (userInitiated || _nanoStatus.value == NanoAvailabilityStatus.AVAILABLE) {
              _toastMessage.value = "On-device Gemini Nano is ready!"
            }
          }.onFailure { err ->
            _nanoDownloadedMb.value = null
            _nanoStatus.value = aiChecklistGenerator.checkNanoStatus()
            if (userInitiated) {
              _toastMessage.value = err.localizedMessage ?: "Gemini Nano download failed; Cloud Gemini remains active."
            }
          }
        }
      }
    }
  }

  fun clearPlannerError() {
    if (_plannerGenerationState.value is UiState.Error) {
      _plannerGenerationState.value = UiState.Idle
    }
  }

  fun clearTripAiEnrichmentError(tripId: String) {
    val current = _tripAiEnrichmentState.value
    if (current[tripId] is UiState.Error) {
      _tripAiEnrichmentState.value = current - tripId
    }
  }

  fun resetPlannerDrafts() {
    _destinationQuery.value = ""
    _placeSuggestions.value = emptyList()
    _selectedPlace.value = null
    _startEpochDay.value = LocalDate.now().plusDays(1).toEpochDay()
    _endEpochDay.value = LocalDate.now().plusDays(5).toEpochDay()
    _selectedActivities.value = setOf("City Walking")
    _weatherForecastState.value = UiState.Idle
    _calendarEvents.value = emptyList()
    _calendarStatusMessage.value = null
    _plannerGenerationState.value = UiState.Idle
  }

  fun onDestinationQueryChange(query: String) {
    _destinationQuery.value = query
    if (_selectedPlace.value?.displayLabel() != query) {
      _selectedPlace.value = null
    }
  }

  fun selectDestinationPlace(place: DestinationPlace) {
    _selectedPlace.value = place
    _destinationQuery.value = place.displayLabel()
    _placeSuggestions.value = emptyList()
    refreshPlannerForecast()
  }

  fun updateTripDates(startEpochDay: Long, endEpochDay: Long) {
    val safeEnd = maxOf(startEpochDay, endEpochDay)
    _startEpochDay.value = startEpochDay
    _endEpochDay.value = safeEnd
    if (_selectedPlace.value != null) {
      refreshPlannerForecast()
    }
  }

  fun toggleActivity(activity: String) {
    val current = _selectedActivities.value
    _selectedActivities.value = if (activity in current) current - activity else current + activity
  }

  fun addCustomActivity(activity: String) {
    val trimmed = activity.trim()
    if (trimmed.isNotBlank()) {
      _selectedActivities.value = _selectedActivities.value + trimmed
    }
  }

  fun refreshPlannerForecast() {
    val place = _selectedPlace.value
    if (place == null) {
      val q = _destinationQuery.value.trim()
      if (q.length < 2) {
        _weatherForecastState.value = UiState.Error("Please enter or select a destination first.")
        return
      }
      viewModelScope.launch {
        _weatherForecastState.value = UiState.Loading
        val resolved = weatherRepository.searchDestinations(q).getOrNull()?.firstOrNull()
        if (resolved == null) {
          _weatherForecastState.value = UiState.Error("Could not find coordinates for $q.")
          return@launch
        }
        _selectedPlace.value = resolved
        fetchForecastInternal(resolved)
      }
    } else {
      viewModelScope.launch {
        _weatherForecastState.value = UiState.Loading
        fetchForecastInternal(place)
      }
    }
  }

  private suspend fun fetchForecastInternal(place: DestinationPlace) {
    weatherRepository.fetchForecastForTrip(
      latitude = place.latitude,
      longitude = place.longitude,
      startEpochDay = _startEpochDay.value,
      endEpochDay = _endEpochDay.value,
      temperatureUnit = homeCountry.value.temperatureUnit,
    ).onSuccess {
      _weatherForecastState.value = UiState.Success(it)
    }.onFailure { err ->
      _weatherForecastState.value = UiState.Error(err.localizedMessage ?: "Failed to fetch live weather.")
    }
  }

  fun loadDeviceCalendarEvents() {
    if (!deviceCalendarReader.hasCalendarPermission()) {
      _calendarStatusMessage.value = "Calendar permission is required to read synced calendar apps."
      return
    }
    val events = deviceCalendarReader.queryEventsForDateRange(_startEpochDay.value, _endEpochDay.value)
    _calendarEvents.value = events
    _calendarStatusMessage.value = if (events.isEmpty()) {
      "No planned events found on device calendars for these dates."
    } else {
      "Loaded ${events.size} planned event(s) from your synced calendar apps."
    }
  }

  fun generateAndCreateTrip(
    weatherNotificationsEnabled: Boolean,
    onCreated: (String) -> Unit,
  ) {
    val destText = _destinationQuery.value.trim()
    if (destText.isBlank()) {
      _toastMessage.value = "Please enter or select a destination first."
      return
    }
    if (_plannerGenerationState.value is UiState.Loading) return

    viewModelScope.launch {
      _plannerGenerationState.value = UiState.Loading

      var place = _selectedPlace.value
      if (place == null) {
        place = withTimeoutOrNull(6_000L) {
          weatherRepository.searchDestinations(destText).getOrNull()?.firstOrNull()
        }
        if (place != null) _selectedPlace.value = place
      }

      var forecast = (_weatherForecastState.value as? UiState.Success)?.data
      if (forecast == null && place != null) {
        forecast = withTimeoutOrNull(6_000L) {
          weatherRepository.fetchForecastForTrip(
            latitude = place.latitude,
            longitude = place.longitude,
            startEpochDay = _startEpochDay.value,
            endEpochDay = _endEpochDay.value,
            temperatureUnit = homeCountry.value.temperatureUnit,
          ).getOrNull()
        }
        if (forecast != null) _weatherForecastState.value = UiState.Success(forecast)
      }

      val durationDays = ((_endEpochDay.value - _startEpochDay.value) + 1L).coerceAtLeast(1L)
      val knownAlwaysBring = (alwaysBringState.value as? UiState.Success)?.data.orEmpty()
      val events = _calendarEvents.value
      val activities = _selectedActivities.value.toList()
      val baseItems = mutableListOf<DraftPackingItem>()
      val seenNames = mutableSetOf<String>()

      val currentUnit = homeCountry.value.temperatureUnit
      val formattedSummary = forecast?.let { WeatherRepository.formatSummaryForUnit(it, currentUnit) }
      val formattedAlert = forecast?.let { WeatherRepository.formatAlertForUnit(it, currentUnit) }.orEmpty()

      forecast?.recommendedGear.orEmpty().forEach { gear ->
        if (seenNames.add(gear.lowercase(Locale.ROOT))) {
          baseItems.add(
            DraftPackingItem(
              name = gear,
              category = "Weather Gear",
              quantity = 1L,
              isAlwaysBring = false,
              contextReason = formattedAlert.ifBlank { formattedSummary ?: "Live weather forecast" },
              sourceType = "weather",
            )
          )
        }
      }

      events.forEach { ev ->
        ev.suggestedItems.forEach { gear ->
          if (seenNames.add(gear.lowercase(Locale.ROOT))) {
            baseItems.add(
              DraftPackingItem(
                name = gear,
                category = "Activity Gear",
                quantity = 1L,
                isAlwaysBring = false,
                contextReason = "Event: ${ev.title}",
                sourceType = "calendar",
              )
            )
          }
        }
      }

      val calSummary = events.map { ev ->
        if (ev.location.isNotBlank()) "${ev.title} (${ev.location})" else ev.title
      }
      val resolvedDest = place?.cityAndRegionLabel()?.ifBlank { destText } ?: destText
      val resolvedCountry = place?.country.orEmpty()
      val weatherSummary = formattedSummary ?: "Seasonal weather (coordinates pending)"
      val headerPhotoUrl = withTimeoutOrNull(2_500L) {
        destinationPhotoRepository.fetchDestinationPhotoUrl(resolvedDest, resolvedCountry)
      }.orEmpty()

      repository.createTripWithItems(
        destination = resolvedDest,
        country = resolvedCountry,
        latitude = place?.latitude ?: 0.0,
        longitude = place?.longitude ?: 0.0,
        startDateEpochDay = _startEpochDay.value,
        endDateEpochDay = _endEpochDay.value,
        durationDays = durationDays,
        activities = activities,
        calendarEventsSummary = calSummary,
        weatherSummary = weatherSummary,
        weatherTempMinC = forecast?.tempMinC ?: 0.0,
        weatherTempMaxC = forecast?.tempMaxC ?: 0.0,
        weatherRainProbMax = forecast?.rainProbMax ?: 0L,
        weatherAlertActive = forecast?.alertActive ?: false,
        weatherAlertMessage = formattedAlert,
        weatherNotificationsEnabled = weatherNotificationsEnabled,
        draftItems = baseItems,
        knownAlwaysBring = knownAlwaysBring,
        headerImageUrl = headerPhotoUrl,
      ).onSuccess { newTripId ->
        _plannerGenerationState.value = UiState.Success(newTripId)
        _selectedTripId.value = newTripId
        val createdTrip = Trip(
          id = newTripId,
          userId = currentUserId,
          destination = resolvedDest,
          country = resolvedCountry,
          latitude = place?.latitude ?: 0.0,
          longitude = place?.longitude ?: 0.0,
          startDateEpochDay = _startEpochDay.value,
          endDateEpochDay = _endEpochDay.value,
          durationDays = durationDays,
          activities = activities,
          calendarEventsSummary = calSummary,
          weatherSummary = weatherSummary,
          weatherTempMinC = forecast?.tempMinC ?: 0.0,
          weatherTempMaxC = forecast?.tempMaxC ?: 0.0,
          weatherRainProbMax = forecast?.rainProbMax ?: 0L,
          weatherAlertActive = forecast?.alertActive ?: false,
          weatherAlertMessage = forecast?.alertMessage.orEmpty(),
          weatherNotificationsEnabled = weatherNotificationsEnabled,
          headerImageUrl = headerPhotoUrl,
        )
        if (headerPhotoUrl.isBlank()) {
          ensureTripHeaderPhotos(listOf(createdTrip))
        }
        enrichTripWithAi(
          trip = createdTrip,
          forecastOverride = forecast,
          calendarEventsOverride = events,
        )
        onCreated(newTripId)
      }.onFailure { err ->
        _plannerGenerationState.value = UiState.Error(err.localizedMessage ?: "Failed to save trip.")
      }
    }
  }

  fun enrichTripWithAi(
    trip: Trip,
    forecastOverride: WeatherForecastSummary? = null,
    calendarEventsOverride: List<CalendarEventInfo> = emptyList(),
  ) {
    if (_tripAiEnrichmentState.value[trip.id] is UiState.Loading) return
    viewModelScope.launch {
      _tripAiEnrichmentState.value = _tripAiEnrichmentState.value + (trip.id to UiState.Loading)

      val existingNames = repository.getTripItemsOnce(trip.id)
        .map { it.name.trim().lowercase(Locale.ROOT) }
        .toSet()
      val alwaysBringNames = (alwaysBringState.value as? UiState.Success)?.data?.map { it.name }.orEmpty()
      val excludedNames = (existingNames + alwaysBringNames.map { it.trim().lowercase(Locale.ROOT) }).toList()

      val forecastSummary = forecastOverride ?: if (trip.weatherSummary.isNotBlank()) {
        WeatherForecastSummary(
          summaryText = trip.weatherSummary,
          tempMinC = trip.weatherTempMinC,
          tempMaxC = trip.weatherTempMaxC,
          rainProbMax = trip.weatherRainProbMax,
          maxWindKmh = 0.0,
          alertActive = trip.weatherAlertActive,
          alertMessage = trip.weatherAlertMessage,
        )
      } else {
        null
      }

      val home = homeCountry.value
      val isIntl = HomeBasePreferences.isTripInternational(home, trip.country)

      val aiResult = aiChecklistGenerator.generateChecklist(
        destination = trip.destination,
        durationDays = trip.durationDays.coerceAtLeast(1L),
        forecast = forecastSummary,
        activities = trip.activities,
        calendarEvents = calendarEventsOverride,
        alwaysBringNames = excludedNames,
        country = trip.country,
        dateRangeLabel = formatTripDateRange(trip),
        homeCountry = home.countryName,
        isInternationalOverride = isIntl,
        onNanoDownloadable = {
          if (!autoDownloadTriggered) {
            autoDownloadTriggered = true
            prepareGeminiNano(userInitiated = false)
          }
        },
      )

      aiResult.onSuccess { generated ->
        _nanoStatus.value = aiChecklistGenerator.checkNanoStatus()
        if (trip.travelGuide == null && generated.travelGuide != null) {
          repository.updateTripTravelGuide(trip.id, generated.travelGuide)
        }
        val freshItems = generated.items.filter { it.name.trim().lowercase(Locale.ROOT) !in existingNames }
        if (freshItems.isEmpty()) {
          _tripAiEnrichmentState.value = _tripAiEnrichmentState.value - trip.id
          _toastMessage.value = "All AI-suggested items are already in your checklist (${generated.engineLabel})."
        } else {
          repository.addPackingItemsBatch(trip.id, freshItems)
            .onSuccess { added ->
              _tripAiEnrichmentState.value = _tripAiEnrichmentState.value - trip.id
              _toastMessage.value = "Added $added item(s) via ${generated.engineLabel}!"
            }
            .onFailure { err ->
              _tripAiEnrichmentState.value = _tripAiEnrichmentState.value + (
                trip.id to UiState.Error(err.localizedMessage ?: "Failed to save AI items.")
              )
            }
        }
      }.onFailure { err ->
        Log.e(TAG, "AI enrichment failed for trip ${trip.id}", err)
        _tripAiEnrichmentState.value = _tripAiEnrichmentState.value + (
          trip.id to UiState.Error(err.localizedMessage ?: "Gemini AI enrichment failed.")
        )
      }
    }
  }

  fun updateTripActivitiesAndSuggest(
    trip: Trip,
    updatedActivities: List<String>,
    newlyAddedActivities: List<String>,
    requestSuggestions: Boolean = true,
  ) {
    val cleanUpdated = updatedActivities
      .map { it.trim().take(60) }
      .filter { it.isNotBlank() }
      .distinctBy { it.lowercase(Locale.ROOT) }
      .take(15)
    val cleanAdded = newlyAddedActivities
      .map { it.trim().take(60) }
      .filter { it.isNotBlank() }
      .distinctBy { it.lowercase(Locale.ROOT) }

    viewModelScope.launch {
      repository.updateTripActivities(trip.id, cleanUpdated)
        .onSuccess {
          val updatedTrip = trip.copy(activities = cleanUpdated)
          if (requestSuggestions) {
            if (cleanAdded.isNotEmpty()) {
              _toastMessage.value = "Added ${cleanAdded.joinToString(", ")} — getting packing suggestions..."
            }
            suggestAiItemsForTrip(
              trip = updatedTrip,
              forceRefresh = true,
              focusActivities = cleanAdded,
            )
          } else {
            _toastMessage.value = if (cleanAdded.isNotEmpty()) {
              "Added ${cleanAdded.joinToString(", ")} to ${trip.destination}."
            } else {
              "Updated planned activities."
            }
          }
        }
        .onFailure { err ->
          _toastMessage.value = err.localizedMessage ?: "Failed to update trip activities."
        }
    }
  }

  fun suggestAiItemsForTrip(
    trip: Trip,
    forceRefresh: Boolean = false,
    focusActivities: List<String> = emptyList(),
  ) {
    val currentState = _tripAiRecommendationsState.value[trip.id]
    if (!forceRefresh) {
      if (currentState is UiState.Loading) return
      if (currentState is UiState.Success && currentState.data.items.isNotEmpty()) return
    }
    if (currentState is UiState.Loading && focusActivities.isEmpty()) return

    viewModelScope.launch {
      _tripAiRecommendationsState.value = _tripAiRecommendationsState.value + (trip.id to UiState.Loading)

      val existingNames = repository.getTripItemsOnce(trip.id)
        .map { it.name.trim().lowercase(Locale.ROOT) }
        .toSet()
      val alwaysBringNames = (alwaysBringState.value as? UiState.Success)?.data?.map { it.name }.orEmpty()
      val excludedNames = (existingNames + alwaysBringNames.map { it.trim().lowercase(Locale.ROOT) }).toList()

      val forecastSummary = if (trip.weatherSummary.isNotBlank()) {
        WeatherForecastSummary(
          summaryText = trip.weatherSummary,
          tempMinC = trip.weatherTempMinC,
          tempMaxC = trip.weatherTempMaxC,
          rainProbMax = trip.weatherRainProbMax,
          maxWindKmh = 0.0,
          alertActive = trip.weatherAlertActive,
          alertMessage = trip.weatherAlertMessage,
        )
      } else {
        null
      }

      val home = homeCountry.value
      val isIntl = HomeBasePreferences.isTripInternational(home, trip.country)

      val aiResult = aiChecklistGenerator.generateChecklist(
        destination = trip.destination,
        durationDays = trip.durationDays.coerceAtLeast(1L),
        forecast = forecastSummary,
        activities = trip.activities,
        calendarEvents = emptyList(),
        alwaysBringNames = excludedNames,
        country = trip.country,
        dateRangeLabel = formatTripDateRange(trip),
        homeCountry = home.countryName,
        isInternationalOverride = isIntl,
        focusActivities = focusActivities,
        onNanoDownloadable = {
          if (!autoDownloadTriggered) {
            autoDownloadTriggered = true
            prepareGeminiNano(userInitiated = false)
          }
        },
      )

      aiResult.onSuccess { generated ->
        _nanoStatus.value = aiChecklistGenerator.checkNanoStatus()
        if (trip.travelGuide == null && generated.travelGuide != null) {
          repository.updateTripTravelGuide(trip.id, generated.travelGuide)
        }
        val focusTokens = focusActivities.map { it.lowercase(Locale.ROOT) }
        val freshItems = generated.items
          .filter { it.name.trim().lowercase(Locale.ROOT) !in existingNames }
          .let { list ->
            if (focusTokens.isEmpty()) {
              list
            } else {
              list.sortedByDescending { draft ->
                val reasonLower = draft.contextReason.lowercase(Locale.ROOT)
                val catLower = draft.category.lowercase(Locale.ROOT)
                when {
                  focusTokens.any { token -> reasonLower.contains(token) } -> 2
                  draft.sourceType.equals("activity", ignoreCase = true) || catLower == "activity gear" -> 1
                  else -> 0
                }
              }
            }
          }
        _tripAiRecommendationsState.value = _tripAiRecommendationsState.value + (
          trip.id to UiState.Success(
            AiItemRecommendation(
              engineLabel = generated.engineLabel,
              items = freshItems,
              focusActivities = focusActivities,
            )
          )
        )
      }.onFailure { err ->
        Log.e(TAG, "AI recommendation generation failed for trip ${trip.id}", err)
        _tripAiRecommendationsState.value = _tripAiRecommendationsState.value + (
          trip.id to UiState.Error(err.localizedMessage ?: "Could not load AI recommendations.")
        )
      }
    }
  }

  fun acceptRecommendedItems(tripId: String, selectedItems: List<DraftPackingItem>) {
    if (selectedItems.isEmpty()) return
    viewModelScope.launch {
      val existingNames = repository.getTripItemsOnce(tripId)
        .map { it.name.trim().lowercase(Locale.ROOT) }
        .toSet()
      val freshToAdd = selectedItems.filter { it.name.trim().lowercase(Locale.ROOT) !in existingNames }
      val acceptedKeys = selectedItems.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()

      if (freshToAdd.isEmpty()) {
        removeRecommendedKeysFromState(tripId, acceptedKeys)
        _toastMessage.value = "Selected item(s) are already in your checklist."
        return@launch
      }

      repository.addPackingItemsBatch(tripId, freshToAdd)
        .onSuccess { added ->
          removeRecommendedKeysFromState(tripId, acceptedKeys)
          _toastMessage.value = "Added $added recommended item(s) to checklist."
        }
        .onFailure { err ->
          _toastMessage.value = err.localizedMessage ?: "Failed to add recommended items."
        }
    }
  }

  fun dismissRecommendedItem(tripId: String, itemName: String) {
    val key = itemName.trim().lowercase(Locale.ROOT)
    if (key.isBlank()) return
    removeRecommendedKeysFromState(tripId, setOf(key))
  }

  fun clearAiRecommendationsError(tripId: String) {
    if (_tripAiRecommendationsState.value[tripId] is UiState.Error) {
      _tripAiRecommendationsState.value = _tripAiRecommendationsState.value - tripId
    }
  }

  private fun removeRecommendedKeysFromState(tripId: String, keysToRemove: Set<String>) {
    val current = _tripAiRecommendationsState.value[tripId] as? UiState.Success ?: return
    val remaining = current.data.items.filterNot {
      it.name.trim().lowercase(Locale.ROOT) in keysToRemove
    }
    _tripAiRecommendationsState.value = _tripAiRecommendationsState.value + (
      tripId to UiState.Success(current.data.copy(items = remaining))
    )
  }

  fun generateTravelGuideForTrip(trip: Trip) {
    if (_tripGuideLoadingState.value[trip.id] is UiState.Loading) return
    viewModelScope.launch {
      _tripGuideLoadingState.value = _tripGuideLoadingState.value + (trip.id to UiState.Loading)
      val home = homeCountry.value
      val isIntl = HomeBasePreferences.isTripInternational(home, trip.country)
      aiChecklistGenerator.generateTravelGuideOnly(
        destination = trip.destination,
        country = trip.country,
        dateRangeLabel = formatTripDateRange(trip),
        activities = trip.activities,
        homeCountry = home.countryName,
        isInternationalOverride = isIntl,
      ).onSuccess { guide ->
        repository.updateTripTravelGuide(trip.id, guide)
          .onSuccess {
            _tripGuideLoadingState.value = _tripGuideLoadingState.value - trip.id
            _toastMessage.value = "Updated Destination Guide for ${trip.destination}."
          }
          .onFailure { err ->
            _tripGuideLoadingState.value = _tripGuideLoadingState.value + (
              trip.id to UiState.Error(err.localizedMessage ?: "Failed to save Destination Guide.")
            )
          }
      }.onFailure { err ->
        _tripGuideLoadingState.value = _tripGuideLoadingState.value + (
          trip.id to UiState.Error(err.localizedMessage ?: "Could not load Destination Guide.")
        )
      }
    }
  }

  fun clearTripGuideError(tripId: String) {
    if (_tripGuideLoadingState.value[tripId] is UiState.Error) {
      _tripGuideLoadingState.value = _tripGuideLoadingState.value - tripId
    }
  }

  fun dismissGuideAttraction(trip: Trip, attraction: AttractionSuggestion) {
    val guide = trip.travelGuide ?: return
    val remaining = guide.nearbyAttractions.filterNot {
      it.name == attraction.name && it.area == attraction.area
    }
    if (remaining.size == guide.nearbyAttractions.size) return
    val updatedGuide = guide.copy(nearbyAttractions = remaining)
    viewModelScope.launch {
      repository.updateTripTravelGuide(trip.id, updatedGuide)
        .onSuccess {
          _toastMessage.value = "Dismissed \"${attraction.name}\"."
        }
        .onFailure { err ->
          _toastMessage.value = err.localizedMessage ?: "Failed to dismiss attraction."
        }
    }
  }

  fun addSuggestedGuideItem(
    tripId: String,
    name: String,
    category: String,
    reason: String,
  ) {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return
    val existingNames = (selectedTripItemsState.value as? UiState.Success)
      ?.data?.map { it.name.trim().lowercase(Locale.ROOT) }?.toSet().orEmpty()
    if (trimmed.lowercase(Locale.ROOT) in existingNames) {
      _toastMessage.value = "\"$trimmed\" is already in your checklist."
      return
    }
    viewModelScope.launch {
      repository.addPackingItem(
        tripId = tripId,
        item = DraftPackingItem(
          name = trimmed,
          category = category.ifBlank { "Tech" },
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = reason.take(180),
          sourceType = "ai",
        ),
      ).onSuccess {
        removeRecommendedKeysFromState(tripId, setOf(trimmed.lowercase(Locale.ROOT)))
        _toastMessage.value = "Added \"$trimmed\" to checklist."
      }.onFailure { err ->
        _toastMessage.value = err.localizedMessage ?: "Failed to add suggested item."
      }
    }
  }

  private fun formatTripDateRange(trip: Trip): String {
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    val start = LocalDate.ofEpochDay(trip.startDateEpochDay).format(fmt)
    val end = LocalDate.ofEpochDay(trip.endDateEpochDay).format(fmt)
    return "$start to $end"
  }

  fun createTripWithoutAi(
    weatherNotificationsEnabled: Boolean,
    onCreated: (String) -> Unit,
  ) {
    val destText = _destinationQuery.value.trim()
    if (destText.isBlank()) {
      _toastMessage.value = "Please enter or select a destination first."
      return
    }
    if (_plannerGenerationState.value is UiState.Loading) return

    viewModelScope.launch {
      _plannerGenerationState.value = UiState.Loading

      var place = _selectedPlace.value
      if (place == null) {
        place = withTimeoutOrNull(6_000L) {
          weatherRepository.searchDestinations(destText).getOrNull()?.firstOrNull()
        }
        if (place != null) _selectedPlace.value = place
      }

      var forecast = (_weatherForecastState.value as? UiState.Success)?.data
      if (forecast == null && place != null) {
        forecast = withTimeoutOrNull(6_000L) {
          weatherRepository.fetchForecastForTrip(
            latitude = place.latitude,
            longitude = place.longitude,
            startEpochDay = _startEpochDay.value,
            endEpochDay = _endEpochDay.value,
            temperatureUnit = homeCountry.value.temperatureUnit,
          ).getOrNull()
        }
        if (forecast != null) _weatherForecastState.value = UiState.Success(forecast)
      }

      val durationDays = ((_endEpochDay.value - _startEpochDay.value) + 1L).coerceAtLeast(1L)
      val knownAlwaysBring = (alwaysBringState.value as? UiState.Success)?.data.orEmpty()
      val events = _calendarEvents.value
      val activities = _selectedActivities.value.toList()
      val draftItems = mutableListOf<DraftPackingItem>()
      val seenNames = mutableSetOf<String>()

      val currentUnit = homeCountry.value.temperatureUnit
      val formattedSummary = forecast?.let { WeatherRepository.formatSummaryForUnit(it, currentUnit) }
      val formattedAlert = forecast?.let { WeatherRepository.formatAlertForUnit(it, currentUnit) }.orEmpty()

      forecast?.recommendedGear.orEmpty().forEach { gear ->
        if (seenNames.add(gear.lowercase(Locale.ROOT))) {
          draftItems.add(
            DraftPackingItem(
              name = gear,
              category = "Weather Gear",
              quantity = 1L,
              isAlwaysBring = false,
              contextReason = formattedAlert.ifBlank { formattedSummary ?: "Live weather forecast" },
              sourceType = "weather",
            )
          )
        }
      }

      events.forEach { ev ->
        ev.suggestedItems.forEach { gear ->
          if (seenNames.add(gear.lowercase(Locale.ROOT))) {
            draftItems.add(
              DraftPackingItem(
                name = gear,
                category = "Activity Gear",
                quantity = 1L,
                isAlwaysBring = false,
                contextReason = "Event: ${ev.title}",
                sourceType = "calendar",
              )
            )
          }
        }
      }

      val calSummary = events.map { ev ->
        if (ev.location.isNotBlank()) "${ev.title} (${ev.location})" else ev.title
      }
      val resolvedDest = place?.cityAndRegionLabel()?.ifBlank { destText } ?: destText
      val resolvedCountry = place?.country.orEmpty()
      val headerPhotoUrl = withTimeoutOrNull(2_500L) {
        destinationPhotoRepository.fetchDestinationPhotoUrl(resolvedDest, resolvedCountry)
      }.orEmpty()

      repository.createTripWithItems(
        destination = resolvedDest,
        country = resolvedCountry,
        latitude = place?.latitude ?: 0.0,
        longitude = place?.longitude ?: 0.0,
        startDateEpochDay = _startEpochDay.value,
        endDateEpochDay = _endEpochDay.value,
        durationDays = durationDays,
        activities = activities,
        calendarEventsSummary = calSummary,
        weatherSummary = formattedSummary ?: "Seasonal weather (coordinates pending)",
        weatherTempMinC = forecast?.tempMinC ?: 0.0,
        weatherTempMaxC = forecast?.tempMaxC ?: 0.0,
        weatherRainProbMax = forecast?.rainProbMax ?: 0L,
        weatherAlertActive = forecast?.alertActive ?: false,
        weatherAlertMessage = formattedAlert,
        weatherNotificationsEnabled = weatherNotificationsEnabled,
        draftItems = draftItems,
        knownAlwaysBring = knownAlwaysBring,
        headerImageUrl = headerPhotoUrl,
      ).onSuccess { newTripId ->
        _plannerGenerationState.value = UiState.Success(newTripId)
        _selectedTripId.value = newTripId
        if (headerPhotoUrl.isBlank()) {
          ensureTripHeaderPhotos(listOf(Trip(id = newTripId, destination = resolvedDest, country = resolvedCountry)))
        }
        onCreated(newTripId)
      }.onFailure { err ->
        _plannerGenerationState.value = UiState.Error(err.localizedMessage ?: "Failed to save trip.")
      }
    }
  }

  fun toggleItemPacked(tripId: String, item: PackingItem) {
    viewModelScope.launch {
      repository.toggleItemPacked(tripId, item.id, !item.isPacked)
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Could not update item." }
    }
  }

  fun addManualItemToTrip(
    tripId: String,
    name: String,
    category: String,
    reason: String,
    quantity: Long,
    scope: TripItemScope,
  ) {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return
    viewModelScope.launch {
      val resolvedCategory = when (scope) {
        TripItemScope.ALWAYS_DO -> CATEGORY_ALWAYS_DO
        else -> category.trim().ifBlank { "Essentials" }
      }
      val resolvedSourceType = when (scope) {
        TripItemScope.ALWAYS_DO -> SOURCE_ALWAYS_DO
        TripItemScope.ALWAYS_BRING -> SOURCE_ALWAYS_BRING
        TripItemScope.TRIP_SPECIFIC -> "manual"
      }
      val resolvedReason = reason.trim().ifBlank {
        when (scope) {
          TripItemScope.ALWAYS_DO -> "Before leaving home on every trip"
          TripItemScope.ALWAYS_BRING -> "Always Bring essential"
          TripItemScope.TRIP_SPECIFIC -> "Added manually"
        }
      }
      val draft = DraftPackingItem(
        name = trimmed,
        category = resolvedCategory,
        quantity = if (scope == TripItemScope.ALWAYS_DO) 1L else quantity.coerceIn(1L, 99L),
        isAlwaysBring = scope == TripItemScope.ALWAYS_BRING,
        contextReason = resolvedReason,
        sourceType = resolvedSourceType,
      )
      repository.addPackingItem(tripId, draft)
        .onSuccess {
          when (scope) {
            TripItemScope.ALWAYS_BRING -> {
              repository.addAlwaysBringItem(trimmed, resolvedCategory, quantity, "Added from trip", "custom")
            }
            TripItemScope.ALWAYS_DO -> {
              repository.addAlwaysBringItem(trimmed, CATEGORY_ALWAYS_DO, 1L, resolvedReason, SOURCE_ALWAYS_DO)
            }
            TripItemScope.TRIP_SPECIFIC -> Unit
          }
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to add item." }
    }
  }

  fun deleteTripItem(tripId: String, itemId: String) {
    viewModelScope.launch {
      repository.deletePackingItem(tripId, itemId)
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to delete item." }
    }
  }

  fun recategorizeTripItem(
    tripId: String,
    item: PackingItem,
    newScope: TripItemScope,
    newCategory: String,
    saveToVault: Boolean,
    newName: String = item.name,
    newQuantity: Long = item.quantity,
    newReason: String = item.contextReason,
  ) {
    val resolvedName = newName.trim().ifBlank { item.name }
    if (resolvedName.isBlank()) return
    viewModelScope.launch {
      if (newScope != TripItemScope.ALWAYS_DO &&
        newCategory.trim().isNotBlank() &&
        CategoryPreferences.DEFAULT_PACKING_CATEGORIES.none { it.equals(newCategory.trim(), ignoreCase = true) }
      ) {
        categoryPreferences.addCustomCategory(newCategory.trim())
      }
      repository.recategorizeTripItem(
        tripId = tripId,
        item = item,
        newScope = newScope,
        newCategory = newCategory,
        saveToVault = saveToVault,
        newName = resolvedName,
        newQuantity = newQuantity,
        newReason = newReason,
      )
        .onSuccess {
          val label = when (newScope) {
            TripItemScope.ALWAYS_DO -> "Always-Do"
            TripItemScope.ALWAYS_BRING -> "Always Bring (${newCategory.ifBlank { "Essentials" }})"
            TripItemScope.TRIP_SPECIFIC -> newCategory.ifBlank { "Trip-Specific" }
          }
          _toastMessage.value = if (!resolvedName.equals(item.name, ignoreCase = false)) {
            "Updated \"$resolvedName\" ($label)"
          } else {
            "Updated \"${item.name}\" ($label)"
          }
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update item." }
    }
  }

  fun addCustomCategory(name: String) {
    val clean = name.trim()
    if (clean.isBlank() || clean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    viewModelScope.launch {
      categoryPreferences.addCustomCategory(clean)
      _toastMessage.value = "Category \"$clean\" added."
    }
  }

  fun deleteCustomCategory(name: String) {
    val clean = name.trim()
    if (clean.isBlank()) return
    viewModelScope.launch {
      categoryPreferences.removeCustomCategory(clean)
      _toastMessage.value = "Removed category \"$clean\"."
    }
  }

  fun renameTripCategory(
    tripId: String,
    oldCategory: String,
    newCategory: String,
    removingCategory: Boolean = false,
  ) {
    val oldClean = oldCategory.trim()
    val newClean = newCategory.trim().ifBlank { "Essentials" }
    if (oldClean.isBlank() || oldClean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    if (newClean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    viewModelScope.launch {
      if (removingCategory) {
        categoryPreferences.removeCustomCategory(oldClean)
      } else {
        val isNewCustom = CategoryPreferences.DEFAULT_PACKING_CATEGORIES.none {
          it.equals(newClean, ignoreCase = true)
        }
        if (isNewCustom) {
          categoryPreferences.renameCustomCategory(oldClean, newClean)
        } else {
          categoryPreferences.removeCustomCategory(oldClean)
        }
      }

      repository.renameTripCategory(tripId, oldClean, newClean)
        .onSuccess { updatedCount ->
          _toastMessage.value = when {
            removingCategory && updatedCount > 0 ->
              "Removed \"$oldClean\" and moved $updatedCount item(s) to $newClean."
            removingCategory ->
              "Removed \"$oldClean\"."
            updatedCount > 0 ->
              "Updated $updatedCount item(s) from \"$oldClean\" to \"$newClean\"."
            else ->
              "Renamed category to \"$newClean\"."
          }
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update category." }
    }
  }

  fun renameVaultCategory(
    oldCategory: String,
    newCategory: String,
    removingCategory: Boolean = false,
  ) {
    val oldClean = oldCategory.trim()
    val newClean = newCategory.trim().ifBlank { "Essentials" }
    if (oldClean.isBlank() || oldClean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    if (newClean.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) return
    viewModelScope.launch {
      if (removingCategory) {
        categoryPreferences.removeCustomCategory(oldClean)
      } else if (CategoryPreferences.DEFAULT_PACKING_CATEGORIES.none { it.equals(newClean, ignoreCase = true) }) {
        categoryPreferences.renameCustomCategory(oldClean, newClean)
      } else {
        categoryPreferences.removeCustomCategory(oldClean)
      }

      repository.renameVaultCategory(oldClean, newClean)
        .onSuccess { updatedCount ->
          _toastMessage.value = when {
            removingCategory && updatedCount > 0 ->
              "Removed \"$oldClean\" and moved $updatedCount vault item(s) to $newClean."
            updatedCount > 0 ->
              "Moved $updatedCount vault item(s) from \"$oldClean\" to \"$newClean\"."
            else ->
              "Category updated to \"$newClean\"."
          }
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update vault category." }
    }
  }

  fun refreshLiveWeatherForTrip(
    trip: Trip,
    onAlertTriggered: (String, WeatherForecastSummary) -> Unit = { _, _ -> },
  ) {
    if (trip.latitude == 0.0 && trip.longitude == 0.0) {
      _toastMessage.value = "No coordinates saved for ${trip.destination}."
      return
    }
    viewModelScope.launch {
      weatherRepository.fetchForecastForTrip(
        latitude = trip.latitude,
        longitude = trip.longitude,
        startEpochDay = trip.startDateEpochDay,
        endEpochDay = trip.endDateEpochDay,
        temperatureUnit = homeCountry.value.temperatureUnit,
      ).onSuccess { forecast ->
        repository.updateTripWeather(trip.id, forecast)
        if (forecast.alertActive && trip.weatherNotificationsEnabled) {
          onAlertTriggered(forecast.alertMessage, forecast)
          _toastMessage.value = "Weather Alert: ${forecast.alertMessage}"
        } else {
          _toastMessage.value = "Forecast updated: ${forecast.summaryText}"
        }
      }.onFailure { err ->
        _toastMessage.value = "Weather refresh failed: ${err.localizedMessage ?: "Network error"}"
      }
    }
  }

  fun addWeatherGearToTrip(tripId: String, forecast: WeatherForecastSummary) {
    val existingNames = (selectedTripItemsState.value as? UiState.Success)
      ?.data?.map { it.name.lowercase(Locale.ROOT) }?.toSet().orEmpty()
    val drafts = forecast.recommendedGear
      .filter { it.lowercase(Locale.ROOT) !in existingNames }
      .map { gear ->
        DraftPackingItem(
          name = gear,
          category = "Weather Gear",
          quantity = 1L,
          isAlwaysBring = false,
          contextReason = forecast.alertMessage.ifBlank { forecast.summaryText },
          sourceType = "weather",
        )
      }
    if (drafts.isEmpty()) {
      _toastMessage.value = "All weather-recommended gear is already on your list."
      return
    }
    viewModelScope.launch {
      repository.addPackingItemsBatch(tripId, drafts)
        .onSuccess { count -> _toastMessage.value = "Added $count weather item(s) to checklist." }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to add weather gear." }
    }
  }

  fun toggleTripWeatherNotifications(trip: Trip, enabled: Boolean) {
    viewModelScope.launch {
      repository.updateTripNotifications(trip.id, enabled)
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update notification setting." }
    }
  }

  fun completeAndResetTrip(trip: Trip, items: List<PackingItem>, markCompletedStatus: Boolean) {
    viewModelScope.launch {
      repository.completeAndResetTrip(trip, items, markCompletedStatus)
        .onSuccess {
          _toastMessage.value = if (markCompletedStatus) {
            "Trip completed & checklist reset fresh for next time!"
          } else {
            "All items unchecked — ${trip.destination} list is fresh for your next journey!"
          }
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to reset trip." }
    }
  }

  fun deleteTrip(tripId: String, onDeleted: () -> Unit) {
    viewModelScope.launch {
      repository.deleteTrip(tripId)
        .onSuccess {
          if (_selectedTripId.value == tripId) _selectedTripId.value = null
          _toastMessage.value = "Trip deleted."
          onDeleted()
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to delete trip." }
    }
  }

  fun addAlwaysBringItem(name: String, category: String, quantity: Long, notes: String) {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return
    viewModelScope.launch {
      val cleanCategory = category.trim().ifBlank { "Essentials" }
      val isAlwaysDo = cleanCategory.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)
      repository.addAlwaysBringItem(
        name = trimmed,
        category = if (isAlwaysDo) CATEGORY_ALWAYS_DO else cleanCategory,
        quantity = if (isAlwaysDo) 1L else quantity,
        notes = notes.ifBlank { if (isAlwaysDo) "Before leaving home on every trip" else "Always bring on every trip" },
        source = if (isAlwaysDo) SOURCE_ALWAYS_DO else "custom",
      )
        .onSuccess {
          _toastMessage.value = if (isAlwaysDo) {
            "Saved \"$trimmed\" to Always-Do tasks."
          } else {
            "Saved \"$trimmed\" to Always Bring."
          }
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save item." }
    }
  }

  fun addAlwaysDoTask(name: String, notes: String) {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return
    viewModelScope.launch {
      repository.addAlwaysBringItem(
        name = trimmed,
        category = CATEGORY_ALWAYS_DO,
        quantity = 1L,
        notes = notes.trim().ifBlank { "Before leaving home on every trip" },
        source = SOURCE_ALWAYS_DO,
      )
        .onSuccess { _toastMessage.value = "Saved \"$trimmed\" to Always-Do tasks." }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save Always-Do task." }
    }
  }

  fun recategorizeVaultItem(
    item: AlwaysBringItem,
    targetType: VaultItemType,
    newCategory: String,
    newNotes: String,
    newName: String = item.name,
    newQuantity: Long = item.quantity,
  ) {
    val resolvedName = newName.trim().ifBlank { item.name }
    if (resolvedName.isBlank()) return
    viewModelScope.launch {
      if (targetType == VaultItemType.ALWAYS_BRING &&
        newCategory.trim().isNotBlank() &&
        CategoryPreferences.DEFAULT_PACKING_CATEGORIES.none { it.equals(newCategory.trim(), ignoreCase = true) }
      ) {
        categoryPreferences.addCustomCategory(newCategory.trim())
      }
      repository.recategorizeVaultItem(
        item = item,
        targetType = targetType,
        newCategory = newCategory,
        newNotes = newNotes,
        newName = resolvedName,
        newQuantity = newQuantity,
      )
        .onSuccess {
          val dest = if (targetType == VaultItemType.ALWAYS_DO) {
            "Always-Do tasks"
          } else {
            "Always Bring (${newCategory.ifBlank { "Essentials" }})"
          }
          _toastMessage.value = "Updated \"$resolvedName\" in $dest"
        }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to update item." }
    }
  }

  fun deleteAlwaysBringItem(itemId: String) {
    viewModelScope.launch {
      repository.deleteAlwaysBringItem(itemId)
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to delete item." }
    }
  }

  fun connectAndLoadGoogleTasks(activity: Activity) {
    viewModelScope.launch {
      _googleTaskListsState.value = UiState.Loading
      workspaceRepository.authorizeScopes(
        activity = activity,
        scopes = listOf(GoogleWorkspaceRepository.SCOPE_TASKS_READONLY),
      ).onSuccess { state ->
        when (state) {
          is WorkspaceAuthState.Authorized -> {
            _googleTasksAccessToken.value = state.accessToken
            loadTaskListsWithToken(state.accessToken)
          }
          is WorkspaceAuthState.NeedsConsent -> {
            _pendingConsentIntent.value = GoogleWorkspacePendingAction.TASKS to state.pendingIntent
          }
        }
      }.onFailure { err ->
        _googleTaskListsState.value = UiState.Error(
          err.localizedMessage ?: "Failed to connect Google Tasks."
        )
      }
    }
  }

  fun fetchGoogleCalendarEvents(activity: Activity) {
    viewModelScope.launch {
      _calendarStatusMessage.value = "Authorizing Google Calendar..."
      workspaceRepository.authorizeScopes(
        activity = activity,
        scopes = listOf(GoogleWorkspaceRepository.SCOPE_CALENDAR_READONLY),
      ).onSuccess { state ->
        when (state) {
          is WorkspaceAuthState.Authorized -> loadGoogleCalendarWithToken(state.accessToken)
          is WorkspaceAuthState.NeedsConsent -> {
            _pendingConsentIntent.value = GoogleWorkspacePendingAction.CALENDAR to state.pendingIntent
          }
        }
      }.onFailure { err ->
        _calendarStatusMessage.value = err.localizedMessage ?: "Google Calendar authorization failed."
      }
    }
  }

  fun handleWorkspaceConsentResult(
    activity: Activity,
    action: GoogleWorkspacePendingAction,
    resultCode: Int,
    data: Intent?,
  ) {
    _pendingConsentIntent.value = null
    if (resultCode != Activity.RESULT_OK || data == null) {
      val cancelMsg = if (action == GoogleWorkspacePendingAction.TASKS) {
        "Google Tasks authorization was cancelled. Switch to the \"Paste or File\" tab to import your list right now."
      } else {
        "Google Calendar OAuth was cancelled or blocked by Google security checks. Use \"Device Calendars\" on the left to read your synced Google Calendar without OAuth."
      }
      if (action == GoogleWorkspacePendingAction.TASKS) {
        _googleTaskListsState.value = UiState.Error(cancelMsg)
      } else {
        _calendarStatusMessage.value = cancelMsg
      }
      return
    }

    workspaceRepository.extractAccessTokenFromIntent(activity, data)
      .onSuccess { token ->
        if (action == GoogleWorkspacePendingAction.TASKS) {
          _googleTasksAccessToken.value = token
          loadTaskListsWithToken(token)
        } else {
          loadGoogleCalendarWithToken(token)
        }
      }
      .onFailure { err ->
        val msg = err.localizedMessage ?: "Failed to extract Google OAuth token."
        if (action == GoogleWorkspacePendingAction.TASKS) {
          _googleTaskListsState.value = UiState.Error(msg)
        } else {
          _calendarStatusMessage.value = msg
        }
      }
  }

  private fun loadGoogleCalendarWithToken(token: String) {
    viewModelScope.launch {
      workspaceRepository.fetchGoogleCalendarEvents(token, _startEpochDay.value, _endEpochDay.value)
        .onSuccess { events ->
          val merged = (_calendarEvents.value + events).distinctBy { "${it.title}_${it.startEpochMillis}" }
          _calendarEvents.value = merged
          _calendarStatusMessage.value = if (events.isEmpty()) {
            "Connected to Google Calendar (no events in selected date range)."
          } else {
            "Loaded ${events.size} planned event(s) from Google Calendar."
          }
        }
        .onFailure { err ->
          _calendarStatusMessage.value = err.localizedMessage ?: "Failed to load Google Calendar events."
        }
    }
  }

  private fun loadTaskListsWithToken(token: String) {
    viewModelScope.launch {
      _googleTaskListsState.value = UiState.Loading
      workspaceRepository.fetchGoogleTaskLists(token)
        .onSuccess { lists ->
          _googleTaskListsState.value = UiState.Success(lists)
          lists.firstOrNull()?.let { selectGoogleTaskList(it.id) }
        }
        .onFailure { err ->
          _googleTasksAccessToken.value = null
          _googleTaskListsState.value = UiState.Error(
            err.localizedMessage ?: "Failed to fetch Google Task lists."
          )
        }
    }
  }

  fun selectGoogleTaskList(listId: String) {
    val token = _googleTasksAccessToken.value ?: return
    _selectedTaskListId.value = listId
    viewModelScope.launch {
      _googleTasksItemsState.value = UiState.Loading
      workspaceRepository.fetchTasksFromList(token, listId)
        .onSuccess { tasks -> _googleTasksItemsState.value = UiState.Success(tasks) }
        .onFailure { err ->
          _googleTasksItemsState.value = UiState.Error(
            err.localizedMessage ?: "Failed to load tasks from list."
          )
        }
    }
  }

  fun importGoogleTasks(
    tasks: List<GoogleTaskItemInfo>,
    targetTripId: String?,
    target: TasksImportTarget,
    onDone: () -> Unit,
  ) {
    if (tasks.isEmpty()) return
    viewModelScope.launch {
      val effectiveTarget = if (targetTripId.isNullOrBlank() && target == TasksImportTarget.SELECTED_TRIP) {
        TasksImportTarget.ALWAYS_BRING
      } else {
        target
      }
      when (effectiveTarget) {
        TasksImportTarget.ALWAYS_DO -> {
          val triples = tasks.map { Triple(it.title, CATEGORY_ALWAYS_DO, it.notes) }
          repository.addAlwaysBringItemsBatch(triples, isAlwaysDo = true)
            .onSuccess { count ->
              _toastMessage.value = "Imported $count Always-Do task(s)."
              onDone()
            }
            .onFailure { _toastMessage.value = it.localizedMessage ?: "Import failed." }
        }
        TasksImportTarget.ALWAYS_BRING -> {
          val triples = tasks.map { Triple(it.title, "Imported", it.notes) }
          repository.addAlwaysBringItemsBatch(triples, isAlwaysDo = false)
            .onSuccess { count ->
              _toastMessage.value = "Imported $count item(s) into Always Bring vault."
              onDone()
            }
            .onFailure { _toastMessage.value = it.localizedMessage ?: "Import failed." }
        }
        TasksImportTarget.SELECTED_TRIP -> {
          val tripId = targetTripId ?: return@launch
          val drafts = tasks.map { task ->
            DraftPackingItem(
              name = task.title,
              category = "Imported",
              quantity = 1L,
              isAlwaysBring = false,
              contextReason = if (task.notes.isNotBlank()) "Google Tasks: ${task.notes}" else "Imported from Google Tasks",
              sourceType = "google_tasks",
            )
          }
          repository.addPackingItemsBatch(tripId, drafts)
            .onSuccess { count ->
              _toastMessage.value = "Imported $count item(s) from Google Tasks!"
              onDone()
            }
            .onFailure { _toastMessage.value = it.localizedMessage ?: "Import failed." }
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    aiChecklistGenerator.close()
  }

  private companion object {
    const val TAG = "PackWiseVM"
  }
}
