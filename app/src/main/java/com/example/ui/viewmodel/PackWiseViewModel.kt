package com.example.ui.viewmodel

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiChecklistGenerator
import com.example.data.firebase.PackingRepository
import com.example.data.local.DeviceCalendarReader
import com.example.data.model.AlwaysBringItem
import com.example.data.model.CalendarEventInfo
import com.example.data.model.DestinationPlace
import com.example.data.model.DraftPackingItem
import com.example.data.model.GoogleTaskItemInfo
import com.example.data.model.GoogleTaskListInfo
import com.example.data.model.PackingItem
import com.example.data.model.Trip
import com.example.data.model.UiState
import com.example.data.model.WeatherForecastSummary
import com.example.data.remote.GoogleWorkspaceRepository
import com.example.data.remote.WeatherRepository
import com.example.data.remote.WorkspaceAuthState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class GoogleWorkspacePendingAction {
  TASKS,
  CALENDAR,
}

@OptIn(ExperimentalCoroutinesApi::class)
class PackWiseViewModel(
  private val repository: PackingRepository,
  private val weatherRepository: WeatherRepository,
  private val aiChecklistGenerator: AiChecklistGenerator,
  private val workspaceRepository: GoogleWorkspaceRepository,
  private val deviceCalendarReader: DeviceCalendarReader,
  private val currentUserId: String,
) : ViewModel() {

  // --- REAL-TIME FIRESTORE STREAMS ---

  val tripsState: StateFlow<UiState<List<Trip>>> = repository.observeTrips(currentUserId)
    .map<List<Trip>, UiState<List<Trip>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val root = firestoreError ?: error
      Log.w(TAG, "Error observing trips", root)
      emit(UiState.Error(root.message ?: "Failed to load trips."))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  val alwaysBringState: StateFlow<UiState<List<AlwaysBringItem>>> =
    repository.observeAlwaysBringItems(currentUserId)
      .map<List<AlwaysBringItem>, UiState<List<AlwaysBringItem>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing always bring items", root)
        emit(UiState.Error(root.message ?: "Failed to load Always Bring essentials."))
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
        flowOf(UiState.Idle)
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
            emit(UiState.Error(root.message ?: "Failed to load packing checklist."))
          }
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Idle,
    )

  // --- TRIP PLANNER STATE ---

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

  private val _selectedActivities = MutableStateFlow<Set<String>>(setOf("City Walking"))
  val selectedActivities: StateFlow<Set<String>> = _selectedActivities.asStateFlow()

  private val _weatherForecastState = MutableStateFlow<UiState<WeatherForecastSummary>>(UiState.Idle)
  val weatherForecastState: StateFlow<UiState<WeatherForecastSummary>> = _weatherForecastState.asStateFlow()

  private val _calendarEvents = MutableStateFlow<List<CalendarEventInfo>>(emptyList())
  val calendarEvents: StateFlow<List<CalendarEventInfo>> = _calendarEvents.asStateFlow()

  private val _calendarStatusMessage = MutableStateFlow<String?>(null)
  val calendarStatusMessage: StateFlow<String?> = _calendarStatusMessage.asStateFlow()

  private val _plannerGenerationState = MutableStateFlow<UiState<String>>(UiState.Idle)
  val plannerGenerationState: StateFlow<UiState<String>> = _plannerGenerationState.asStateFlow()

  // --- GOOGLE TASKS / CALENDAR OAUTH IMPORT STATE ---

  private val _googleTasksAccessToken = MutableStateFlow<String?>(null)
  private val _googleTaskListsState = MutableStateFlow<UiState<List<GoogleTaskListInfo>>>(UiState.Idle)
  val googleTaskListsState: StateFlow<UiState<List<GoogleTaskListInfo>>> = _googleTaskListsState.asStateFlow()

  private val _selectedTaskListId = MutableStateFlow<String?>(null)
  val selectedTaskListId: StateFlow<String?> = _selectedTaskListId.asStateFlow()

  private val _googleTasksItemsState = MutableStateFlow<UiState<List<GoogleTaskItemInfo>>>(UiState.Idle)
  val googleTasksItemsState: StateFlow<UiState<List<GoogleTaskItemInfo>>> = _googleTasksItemsState.asStateFlow()

  private val _pendingConsentIntent = MutableStateFlow<Pair<GoogleWorkspacePendingAction, PendingIntent>?>(null)
  val pendingConsentIntent: StateFlow<Pair<GoogleWorkspacePendingAction, PendingIntent>?> =
    _pendingConsentIntent.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  init {
    viewModelScope.launch {
      repository.seedDefaultAlwaysBringIfEmpty()
    }
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  fun showToast(msg: String) {
    _toastMessage.value = msg
  }

  fun selectTrip(tripId: String?) {
    _selectedTripId.value = tripId
  }

  // --- PLANNER INPUT HANDLERS ---

  fun onDestinationQueryChange(query: String) {
    _destinationQuery.value = query
    if (_selectedPlace.value?.displayLabel != query) {
      _selectedPlace.value = null
    }
    if (query.trim().length < 2) {
      _placeSuggestions.value = emptyList()
      return
    }
    viewModelScope.launch {
      weatherRepository.searchDestinations(query)
        .onSuccess { places -> _placeSuggestions.value = places }
        .onFailure { _placeSuggestions.value = emptyList() }
    }
  }

  fun selectDestinationPlace(place: DestinationPlace) {
    _selectedPlace.value = place
    _destinationQuery.value = place.displayLabel
    _placeSuggestions.value = emptyList()
    refreshPlannerForecast()
  }

  fun updateTripDates(startEpoch: Long, endEpoch: Long) {
    val safeEnd = endEpoch.coerceAtLeast(startEpoch)
    _startEpochDay.value = startEpoch
    _endEpochDay.value = safeEnd
    if (_selectedPlace.value != null) {
      refreshPlannerForecast()
    }
  }

  fun toggleActivity(activity: String) {
    val current = _selectedActivities.value.toMutableSet()
    if (!current.add(activity)) {
      current.remove(activity)
    }
    _selectedActivities.value = current
  }

  fun addCustomActivity(activity: String) {
    val clean = activity.trim()
    if (clean.isNotBlank()) {
      _selectedActivities.value = _selectedActivities.value + clean
    }
  }

  fun refreshPlannerForecast() {
    val place = _selectedPlace.value ?: return
    viewModelScope.launch {
      _weatherForecastState.value = UiState.Loading
      weatherRepository.fetchForecastForTrip(
        latitude = place.latitude,
        longitude = place.longitude,
        startEpochDay = _startEpochDay.value,
        endEpochDay = _endEpochDay.value,
      ).onSuccess { summary ->
        _weatherForecastState.value = UiState.Success(summary)
      }.onFailure { err ->
        _weatherForecastState.value = UiState.Error(err.localizedMessage ?: "Failed to fetch live weather.")
      }
    }
  }

  fun loadDeviceCalendarEvents() {
    if (!deviceCalendarReader.hasCalendarPermission()) {
      _calendarStatusMessage.value = "Calendar permission is required to read synced calendar apps."
      return
    }
    val events = deviceCalendarReader.readEventsForRange(_startEpochDay.value, _endEpochDay.value)
    _calendarEvents.value = events
    _calendarStatusMessage.value = if (events.isEmpty()) {
      "No planned events found on device calendars for these dates."
    } else {
      "Loaded ${events.size} planned event(s) from your synced calendar apps."
    }
  }

  fun fetchGoogleCalendarEvents(activity: Activity) {
    viewModelScope.launch {
      _calendarStatusMessage.value = "Connecting to Google Calendar..."
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
        _calendarStatusMessage.value = "Google Calendar error: ${err.localizedMessage ?: "Authorization failed"}"
      }
    }
  }

  private fun loadGoogleCalendarWithToken(token: String) {
    viewModelScope.launch {
      workspaceRepository.fetchGoogleCalendarEvents(
        accessToken = token,
        startEpochDay = _startEpochDay.value,
        endEpochDay = _endEpochDay.value,
      ).onSuccess { googleEvents ->
        val merged = (_calendarEvents.value + googleEvents)
          .distinctBy { "${it.title}_${it.startEpochMillis}" }
          .take(25)
        _calendarEvents.value = merged
        _calendarStatusMessage.value = if (merged.isEmpty()) {
          "No Google Calendar events found for these dates."
        } else {
          "Synced ${merged.size} calendar event(s) for trip context."
        }
      }.onFailure { err ->
        _calendarStatusMessage.value = "Could not load Google Calendar: ${err.localizedMessage}"
      }
    }
  }

  // --- GENERATE & SAVE TRIP CHECKLIST ---

  fun generateAndCreateTrip(
    weatherNotificationsEnabled: Boolean,
    onCreated: (String) -> Unit,
  ) {
    val place = _selectedPlace.value
    val destinationName = place?.name ?: _destinationQuery.value.trim()
    if (destinationName.isBlank()) {
      _plannerGenerationState.value = UiState.Error("Please enter or select a destination first.")
      return
    }
    val startEpoch = _startEpochDay.value
    val endEpoch = _endEpochDay.value.coerceAtLeast(startEpoch)
    val durationDays = (endEpoch - startEpoch + 1L).coerceIn(1L, 365L)

    viewModelScope.launch {
      _plannerGenerationState.value = UiState.Loading

      // Resolve coordinates & live weather if user typed a city without tapping autocomplete
      val resolvedPlace = place ?: weatherRepository.searchDestinations(destinationName)
        .getOrNull()?.firstOrNull()

      val lat = resolvedPlace?.latitude ?: 0.0
      val lon = resolvedPlace?.longitude ?: 0.0
      val country = resolvedPlace?.country.orEmpty()

      val forecast = if (resolvedPlace != null) {
        (weatherForecastState.value as? UiState.Success)?.data
          ?: weatherRepository.fetchForecastForTrip(lat, lon, startEpoch, endEpoch).getOrNull()
      } else null

      val alwaysBring = (alwaysBringState.value as? UiState.Success)?.data.orEmpty()
      val events = _calendarEvents.value
      val activities = _selectedActivities.value.toList()

      val aiResult = aiChecklistGenerator.generateChecklist(
        destination = resolvedPlace?.name ?: destinationName,
        country = country,
        durationDays = durationDays,
        activities = activities,
        weather = forecast,
        calendarEvents = events,
        alwaysBringItems = alwaysBring,
      )

      val aiItems = aiResult.getOrElse { err ->
        Log.e(TAG, "AI generation error", err)
        _plannerGenerationState.value = UiState.Error(
          err.localizedMessage ?: "Gemini AI failed to generate checklist."
        )
        return@launch
      }

      // Combine Always Bring essentials + AI-generated items + any direct calendar/weather items (deduped)
      val drafts = mutableListOf<DraftPackingItem>()
      val seenNames = mutableSetOf<String>()

      alwaysBring.forEach { ab ->
        if (seenNames.add(ab.name.lowercase())) {
          drafts.add(
            DraftPackingItem(
              name = ab.name,
              category = ab.category,
              quantity = ab.quantity,
              isAlwaysBring = true,
              contextReason = "Always Bring essential",
              sourceType = "always_bring",
            )
          )
        }
      }

      events.flatMap { it.suggestedItems }.forEach { calItem ->
        if (seenNames.add(calItem.name.lowercase())) {
          drafts.add(calItem)
        }
      }

      forecast?.recommendedGear.orEmpty().forEach { wItem ->
        if (seenNames.add(wItem.name.lowercase())) {
          drafts.add(wItem)
        }
      }

      aiItems.forEach { item ->
        if (seenNames.add(item.name.lowercase())) {
          drafts.add(item)
        }
      }

      val eventSummaries = events.map { DeviceCalendarReader.formatEventSummary(it) }.take(25)

      repository.createTripWithItems(
        destination = resolvedPlace?.name ?: destinationName,
        country = country,
        latitude = lat,
        longitude = lon,
        startDateEpochDay = startEpoch,
        endDateEpochDay = endEpoch,
        durationDays = durationDays,
        activities = activities,
        calendarEventsSummary = eventSummaries,
        weatherSummary = forecast?.summaryText ?: "Seasonal weather (coordinates pending)",
        weatherTempMinC = forecast?.tempMinC ?: 15.0,
        weatherTempMaxC = forecast?.tempMaxC ?: 22.0,
        weatherRainProbMax = forecast?.rainProbMax ?: 0L,
        weatherAlertActive = forecast?.alertActive ?: false,
        weatherAlertMessage = forecast?.alertMessage ?: "",
        weatherNotificationsEnabled = weatherNotificationsEnabled,
        draftItems = drafts,
      ).onSuccess { newTripId ->
        _selectedTripId.value = newTripId
        _plannerGenerationState.value = UiState.Success(newTripId)
        resetPlannerDrafts()
        onCreated(newTripId)
      }.onFailure { err ->
        _plannerGenerationState.value = UiState.Error(err.localizedMessage ?: "Failed to save trip.")
      }
    }
  }

  fun clearPlannerError() {
    if (_plannerGenerationState.value is UiState.Error) {
      _plannerGenerationState.value = UiState.Idle
    }
  }

  private fun resetPlannerDrafts() {
    _destinationQuery.value = ""
    _selectedPlace.value = null
    _placeSuggestions.value = emptyList()
    _weatherForecastState.value = UiState.Idle
    _calendarEvents.value = emptyList()
    _calendarStatusMessage.value = null
  }

  // --- TRIP DETAIL ACTIONS ---

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
    quantity: Long,
    alsoSaveAsAlwaysBring: Boolean,
  ) {
    val clean = name.trim()
    if (clean.isBlank()) return
    viewModelScope.launch {
      if (alsoSaveAsAlwaysBring) {
        repository.addAlwaysBringItem(clean, category, quantity, "Added from trip", "custom")
      }
      repository.addPackingItem(
        tripId = tripId,
        draft = DraftPackingItem(
          name = clean,
          category = category,
          quantity = quantity,
          isAlwaysBring = alsoSaveAsAlwaysBring,
          contextReason = if (alsoSaveAsAlwaysBring) "Always Bring essential" else "Added manually",
          sourceType = if (alsoSaveAsAlwaysBring) "always_bring" else "custom",
        )
      ).onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to add item." }
    }
  }

  fun promoteToAlwaysBring(tripId: String, item: PackingItem) {
    viewModelScope.launch {
      repository.promoteItemToAlwaysBring(tripId, item)
        .onSuccess { _toastMessage.value = "\"${item.name}\" added to Always Bring vault." }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to promote item." }
    }
  }

  fun deleteTripItem(tripId: String, itemId: String) {
    viewModelScope.launch {
      repository.deletePackingItem(tripId, itemId)
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to delete item." }
    }
  }

  fun refreshLiveWeatherForTrip(
    trip: Trip,
    onAlertTriggered: (String, String) -> Unit = { _, _ -> },
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
      ).onSuccess { forecast ->
        repository.updateTripWeather(trip.id, forecast)
        if (forecast.alertActive && forecast.alertMessage.isNotBlank()) {
          _toastMessage.value = "Weather Alert: ${forecast.alertMessage}"
          onAlertTriggered(trip.destination, forecast.alertMessage)
        } else {
          _toastMessage.value = "Forecast updated: ${forecast.summaryText}"
        }
      }.onFailure { err ->
        _toastMessage.value = "Weather refresh failed: ${err.localizedMessage}"
      }
    }
  }

  fun addWeatherGearToTrip(tripId: String, forecast: WeatherForecastSummary) {
    viewModelScope.launch {
      val existingNames = ((selectedTripItemsState.value as? UiState.Success)?.data.orEmpty())
        .map { it.name.lowercase() }
        .toSet()
      val missing = forecast.recommendedGear.filter { it.name.lowercase() !in existingNames }
      if (missing.isEmpty()) {
        _toastMessage.value = "All weather-recommended gear is already on your list."
        return@launch
      }
      repository.addPackingItemsBatch(tripId, missing)
        .onSuccess { count -> _toastMessage.value = "Added $count weather item(s) to checklist." }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to add weather gear." }
    }
  }

  fun toggleTripWeatherNotifications(trip: Trip, enabled: Boolean) {
    viewModelScope.launch {
      repository.updateTripNotifications(trip.id, enabled)
    }
  }

  fun completeAndResetTrip(trip: Trip, items: List<PackingItem>, reopenImmediately: Boolean) {
    viewModelScope.launch {
      repository.completeAndResetTrip(trip, items, reopenImmediately)
        .onSuccess {
          _toastMessage.value = if (reopenImmediately) {
            "All items unchecked — ${trip.destination} list is fresh for your next journey!"
          } else {
            "Trip completed & checklist reset fresh for next time!"
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

  // --- ALWAYS BRING VAULT ACTIONS ---

  fun addAlwaysBringItem(name: String, category: String, quantity: Long, notes: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      repository.addAlwaysBringItem(name, category, quantity, notes, "custom")
        .onSuccess { _toastMessage.value = "Saved \"$name\" to Always Bring." }
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save item." }
    }
  }

  fun deleteAlwaysBringItem(itemId: String) {
    viewModelScope.launch {
      repository.deleteAlwaysBringItem(itemId)
        .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to delete item." }
    }
  }

  // --- GOOGLE TASKS IMPORT ---

  fun connectAndLoadGoogleTasks(activity: Activity) {
    val cachedToken = _googleTasksAccessToken.value
    if (!cachedToken.isNullOrBlank()) {
      loadTaskListsWithToken(cachedToken)
      return
    }
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
          err.localizedMessage ?: "Could not connect to Google Tasks."
        )
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
      if (action == GoogleWorkspacePendingAction.TASKS) {
        _googleTaskListsState.value = UiState.Error(
          "Google Tasks sign-in was cancelled or blocked by Google's unverified-app security check. " +
            "Add your email as a Test User in Google Cloud Console (OAuth Consent Screen) and enable Google Tasks API, " +
            "or switch to \"Paste / File Import\" right away."
        )
      } else {
        _calendarStatusMessage.value =
          "Google Calendar OAuth was cancelled or blocked by Google security checks. Use \"Device Calendars\" on the left to read your synced Google Calendar without OAuth."
      }
      return
    }
    workspaceRepository.extractAccessTokenFromIntent(activity, data)
      .onSuccess { token ->
        when (action) {
          GoogleWorkspacePendingAction.TASKS -> {
            _googleTasksAccessToken.value = token
            loadTaskListsWithToken(token)
          }
          GoogleWorkspacePendingAction.CALENDAR -> loadGoogleCalendarWithToken(token)
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
    importAsAlwaysBring: Boolean,
    onDone: () -> Unit,
  ) {
    if (tasks.isEmpty()) return
    viewModelScope.launch {
      if (importAsAlwaysBring || targetTripId.isNullOrBlank()) {
        val triples = tasks.map { Triple(it.title, "Imported", it.notes) }
        repository.addAlwaysBringItemsBatch(triples)
          .onSuccess { count ->
            _toastMessage.value = "Imported $count item(s) into Always Bring vault."
            onDone()
          }
          .onFailure { _toastMessage.value = it.localizedMessage ?: "Import failed." }
      } else {
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
        repository.addPackingItemsBatch(targetTripId, drafts)
          .onSuccess { count ->
            _toastMessage.value = "Imported $count item(s) from Google Tasks!"
            onDone()
          }
          .onFailure { _toastMessage.value = it.localizedMessage ?: "Import failed." }
      }
    }
  }

  private companion object {
    const val TAG = "PackWiseVM"
  }
}
