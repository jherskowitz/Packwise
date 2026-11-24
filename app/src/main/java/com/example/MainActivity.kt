package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ai.AiChecklistGenerator
import com.example.data.firebase.PackingRepository
import com.example.data.firebase.configureAppCheck
import com.example.data.local.DeviceCalendarReader
import com.example.data.local.HomeBasePreferences
import com.example.data.model.TasksImportTarget
import com.example.data.model.UiState
import com.example.data.model.VaultItemType
import com.example.data.remote.DestinationPhotoRepository
import com.example.data.remote.GoogleWorkspaceRepository
import com.example.data.remote.WeatherRepository
import com.example.notifications.WeatherAlertWorker
import com.example.notifications.WeatherNotificationHelper
import com.example.ui.screens.AlwaysBringScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GoogleTasksImportSheet
import com.example.ui.screens.HomeTripsScreen
import com.example.ui.screens.IntegrationsSettingsScreen
import com.example.ui.screens.TripDetailScreen
import com.example.ui.screens.TripPlannerScreen
import com.example.ui.theme.PackWiseTheme
import com.example.ui.viewmodel.PackWiseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

  private val incomingSharedText = MutableStateFlow<String?>(null)
  private val incomingTripId = MutableStateFlow<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    configureAppCheck(this, intent)
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WeatherNotificationHelper.ensureChannel(this)
    WeatherAlertWorker.schedulePeriodic(this)
    handleIntentExtras(intent)

    setContent {
      PackWiseTheme {
        val sharedText by incomingSharedText.collectAsStateWithLifecycle()
        val notificationTripId by incomingTripId.collectAsStateWithLifecycle()

        PackWiseRoot(
          sharedText = sharedText,
          onConsumeSharedText = { incomingSharedText.value = null },
          notificationTripId = notificationTripId,
          onConsumeNotificationTripId = { incomingTripId.value = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntentExtras(intent)
  }

  private fun handleIntentExtras(intent: Intent?) {
    if (intent == null) return
    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
      val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
      if (!shared.isNullOrBlank()) {
        incomingSharedText.value = shared
      }
    }
    val tripId = intent.getStringExtra(WeatherNotificationHelper.EXTRA_TRIP_ID)?.trim()
    if (!tripId.isNullOrBlank()) {
      incomingTripId.value = tripId
    }
  }
}

@Composable
private fun PackWiseRoot(
  sharedText: String?,
  onConsumeSharedText: () -> Unit,
  notificationTripId: String?,
  onConsumeNotificationTripId: () -> Unit,
) {
  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

  DisposableEffect(Unit) {
    val auth = Firebase.auth
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      currentUser = firebaseAuth.currentUser
    }
    auth.addAuthStateListener(listener)
    onDispose { auth.removeAuthStateListener(listener) }
  }

  val user = currentUser
  if (user == null) {
    AuthScreen(
      onAuthSuccess = { currentUser = Firebase.auth.currentUser },
    )
  } else {
    key(user.uid) {
      PackWiseAppShell(
        userId = user.uid,
        sharedText = sharedText,
        onConsumeSharedText = onConsumeSharedText,
        notificationTripId = notificationTripId,
        onConsumeNotificationTripId = onConsumeNotificationTripId,
      )
    }
  }
}

@Composable
private fun PackWiseAppShell(
  userId: String,
  sharedText: String?,
  onConsumeSharedText: () -> Unit,
  notificationTripId: String?,
  onConsumeNotificationTripId: () -> Unit,
) {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val viewModel: PackWiseViewModel = viewModel(
    key = "packwise_vm_$userId",
    factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PackWiseViewModel(
          repository = PackingRepository(appContext),
          weatherRepository = WeatherRepository(),
          destinationPhotoRepository = DestinationPhotoRepository(),
          aiChecklistGenerator = AiChecklistGenerator(),
          workspaceRepository = GoogleWorkspaceRepository(),
          deviceCalendarReader = DeviceCalendarReader(appContext),
          homeBasePreferences = HomeBasePreferences(appContext),
          currentUserId = userId,
        ) as T
      }
    },
  )

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: "home"
  val snackbarHostState = remember { SnackbarHostState() }

  val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
  val alwaysBringState by viewModel.alwaysBringState.collectAsStateWithLifecycle()
  val selectedTripItemsState by viewModel.selectedTripItemsState.collectAsStateWithLifecycle()
  val tripAiEnrichmentMap by viewModel.tripAiEnrichmentState.collectAsStateWithLifecycle()
  val tripAiRecommendationsMap by viewModel.tripAiRecommendationsState.collectAsStateWithLifecycle()
  val tripGuideLoadingMap by viewModel.tripGuideLoadingState.collectAsStateWithLifecycle()
  val homeCountry by viewModel.homeCountry.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
  val pendingConsent by viewModel.pendingConsentIntent.collectAsStateWithLifecycle()
  val nanoStatus by viewModel.nanoStatus.collectAsStateWithLifecycle()
  val nanoDownloadedMb by viewModel.nanoDownloadedMb.collectAsStateWithLifecycle()

  var showGoogleTasksSheet by remember { mutableStateOf(false) }
  var sheetTripId by remember { mutableStateOf<String?>(null) }
  var sheetTripName by remember { mutableStateOf<String?>(null) }
  var sheetDefaultTarget by remember { mutableStateOf(TasksImportTarget.ALWAYS_BRING) }
  var sheetSharedText by remember { mutableStateOf<String?>(null) }

  val consentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult(),
    onResult = { result ->
      val activity = context as? ComponentActivity
      val action = pendingConsent?.first
      if (activity != null && action != null) {
        viewModel.handleWorkspaceConsentResult(
          activity = activity,
          action = action,
          resultCode = result.resultCode,
          data = result.data,
        )
      }
    },
  )

  LaunchedEffect(pendingConsent) {
    val pair = pendingConsent ?: return@LaunchedEffect
    consentLauncher.launch(IntentSenderRequest.Builder(pair.second).build())
  }

  LaunchedEffect(toastMessage) {
    val msg = toastMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearToast()
  }

  LaunchedEffect(sharedText) {
    if (!sharedText.isNullOrBlank()) {
      sheetTripId = null
      sheetTripName = null
      sheetDefaultTarget = TasksImportTarget.ALWAYS_BRING
      sheetSharedText = sharedText
      showGoogleTasksSheet = true
      onConsumeSharedText()
    }
  }

  LaunchedEffect(notificationTripId) {
    if (!notificationTripId.isNullOrBlank()) {
      viewModel.selectTrip(notificationTripId)
      navController.navigate("trip_detail/$notificationTripId") {
        launchSingleTop = true
      }
      onConsumeNotificationTripId()
    }
  }

  val showTopLevelNav = currentRoute in setOf("home", "always_bring", "integrations")
  val vaultItems = (alwaysBringState as? UiState.Success)?.data.orEmpty()
  val alwaysBringCount = vaultItems.count { !it.isAlwaysDo }
  val alwaysDoCount = vaultItems.count { it.isAlwaysDo }
  val trips = (tripsState as? UiState.Success)?.data.orEmpty()

  LaunchedEffect(trips) {
    if (trips.isNotEmpty()) {
      viewModel.ensureTripHeaderPhotos(trips)
    }
  }

  val openTasksSheetFor: (String?, String?, TasksImportTarget) -> Unit = { tripId, tripName, defaultTarget ->
    sheetTripId = tripId
    sheetTripName = tripName
    sheetDefaultTarget = defaultTarget
    sheetSharedText = null
    showGoogleTasksSheet = true
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 700.dp

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (showTopLevelNav && !useRail) {
          NavigationBar {
            NavigationBarItem(
              selected = currentRoute == "home",
              onClick = {
                navController.navigate("home") {
                  popUpTo("home") { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = { Icon(Icons.Rounded.FlightTakeoff, contentDescription = null) },
              label = { Text("Trips") },
              modifier = Modifier.testTag("nav_trips"),
            )
            NavigationBarItem(
              selected = currentRoute == "always_bring",
              onClick = {
                navController.navigate("always_bring") {
                  popUpTo("home") { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
              label = { Text("Always") },
              modifier = Modifier.testTag("nav_always_bring"),
            )
            NavigationBarItem(
              selected = currentRoute == "integrations",
              onClick = {
                navController.navigate("integrations") {
                  popUpTo("home") { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
              label = { Text("Integrations") },
              modifier = Modifier.testTag("nav_integrations"),
            )
          }
        }
      },
      floatingActionButton = {
        if (currentRoute == "home") {
          FloatingActionButton(
            onClick = {
              viewModel.resetPlannerDrafts()
              navController.navigate("planner")
            },
            modifier = Modifier.testTag("home_plan_trip_fab"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Plan New Trip")
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (showTopLevelNav && useRail) {
          NavigationRail {
            NavigationRailItem(
              selected = currentRoute == "home",
              onClick = {
                navController.navigate("home") {
                  popUpTo("home") { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = { Icon(Icons.Rounded.FlightTakeoff, contentDescription = null) },
              label = { Text("Trips") },
              modifier = Modifier.testTag("nav_trips"),
            )
            NavigationRailItem(
              selected = currentRoute == "always_bring",
              onClick = {
                navController.navigate("always_bring") {
                  popUpTo("home") { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = { Icon(Icons.Rounded.Shield, contentDescription = null) },
              label = { Text("Always") },
              modifier = Modifier.testTag("nav_always_bring"),
            )
            NavigationRailItem(
              selected = currentRoute == "integrations",
              onClick = {
                navController.navigate("integrations") {
                  popUpTo("home") { inclusive = false }
                  launchSingleTop = true
                }
              },
              icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
              label = { Text("Integrations") },
              modifier = Modifier.testTag("nav_integrations"),
            )
          }
        }

        Box(modifier = Modifier.weight(1f)) {
          NavHost(
            navController = navController,
            startDestination = "home",
          ) {
            composable("home") {
              HomeTripsScreen(
                tripsState = tripsState,
                alwaysBringCount = alwaysBringCount,
                alwaysDoCount = alwaysDoCount,
                onPlanNewTrip = {
                  viewModel.resetPlannerDrafts()
                  navController.navigate("planner")
                },
                onOpenTrip = { tripId ->
                  viewModel.selectTrip(tripId)
                  navController.navigate("trip_detail/$tripId")
                },
                onOpenAlwaysBring = {
                  navController.navigate("always_bring") {
                    popUpTo("home") { inclusive = false }
                    launchSingleTop = true
                  }
                },
                onOpenGoogleTasksImport = {
                  val active = trips.firstOrNull()
                  openTasksSheetFor(
                    active?.id,
                    active?.destination,
                    if (active != null) TasksImportTarget.SELECTED_TRIP else TasksImportTarget.ALWAYS_BRING,
                  )
                },
              )
            }

            composable("always_bring") {
              AlwaysBringScreen(
                alwaysBringState = alwaysBringState,
                onAddAlwaysBring = viewModel::addAlwaysBringItem,
                onAddAlwaysDoTask = viewModel::addAlwaysDoTask,
                onRecategorizeVaultItem = viewModel::recategorizeVaultItem,
                onDeleteAlwaysBring = viewModel::deleteAlwaysBringItem,
                onImportGoogleTasks = { activeTab ->
                  val target = if (activeTab == VaultItemType.ALWAYS_DO) {
                    TasksImportTarget.ALWAYS_DO
                  } else {
                    TasksImportTarget.ALWAYS_BRING
                  }
                  openTasksSheetFor(null, null, target)
                },
              )
            }

            composable("integrations") {
              IntegrationsSettingsScreen(
                nanoStatus = nanoStatus,
                nanoDownloadedMb = nanoDownloadedMb,
                homeCountry = homeCountry,
                onSetManualHomeCountry = viewModel::setManualHomeCountry,
                onResetHomeCountryToDeviceRegion = viewModel::resetHomeCountryToDeviceRegion,
                onDetectHomeCountryFromLocation = viewModel::detectHomeCountryFromLocation,
                onPrepareGeminiNano = { viewModel.prepareGeminiNano(userInitiated = true) },
                onRefreshNanoStatus = viewModel::refreshNanoStatus,
                onOpenGoogleTasksImport = {
                  val active = trips.firstOrNull()
                  openTasksSheetFor(
                    active?.id,
                    active?.destination,
                    if (active != null) TasksImportTarget.SELECTED_TRIP else TasksImportTarget.ALWAYS_BRING,
                  )
                },
                onShowFeedback = viewModel::showToast,
              )
            }

            composable("planner") {
              TripPlannerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = { newTripId ->
                  navController.navigate("trip_detail/$newTripId") {
                    popUpTo("home") { inclusive = false }
                  }
                },
              )
            }

            composable(
              route = "trip_detail/{tripId}",
              arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
            ) { backStackEntry ->
              val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
              LaunchedEffect(tripId) {
                if (tripId.isNotBlank()) viewModel.selectTrip(tripId)
              }
              val trip = trips.firstOrNull { it.id == tripId }
              if (trip != null) {
                TripDetailScreen(
                  trip = trip,
                  itemsState = selectedTripItemsState,
                  aiEnrichmentState = tripAiEnrichmentMap[trip.id] ?: UiState.Idle,
                  aiRecommendationsState = tripAiRecommendationsMap[trip.id] ?: UiState.Idle,
                  guideLoadingState = tripGuideLoadingMap[trip.id] ?: UiState.Idle,
                  homeCountry = homeCountry,
                  onNavigateBack = { navController.popBackStack() },
                  onTogglePacked = { item -> viewModel.toggleItemPacked(trip.id, item) },
                  onAddManualItem = { name, category, reason, qty, scope ->
                    viewModel.addManualItemToTrip(trip.id, name, category, reason, qty, scope)
                  },
                  onDeleteItem = { itemId -> viewModel.deleteTripItem(trip.id, itemId) },
                  onRecategorizeItem = { item, newScope, newCategory, saveToVault ->
                    viewModel.recategorizeTripItem(trip.id, item, newScope, newCategory, saveToVault)
                  },
                  onRefreshWeather = {
                    viewModel.refreshLiveWeatherForTrip(trip) { alertMessage, _ ->
                      WeatherNotificationHelper.showWeatherPackingAlert(
                        context = appContext,
                        tripId = trip.id,
                        destination = trip.destination,
                        message = alertMessage,
                      )
                    }
                  },
                  onToggleNotifications = { enabled ->
                    viewModel.toggleTripWeatherNotifications(trip, enabled)
                  },
                  onOpenGoogleTasksImport = {
                    openTasksSheetFor(trip.id, trip.destination, TasksImportTarget.SELECTED_TRIP)
                  },
                  onOpenAiRecommendations = { forceRefresh ->
                    viewModel.suggestAiItemsForTrip(trip, forceRefresh = forceRefresh)
                  },
                  onAddRecommendedItems = { selected ->
                    viewModel.acceptRecommendedItems(trip.id, selected)
                  },
                  onDismissRecommendedItem = { itemName ->
                    viewModel.dismissRecommendedItem(trip.id, itemName)
                  },
                  onClearAiRecommendationsError = {
                    viewModel.clearAiRecommendationsError(trip.id)
                  },
                  onRetryAiEnrichment = { viewModel.enrichTripWithAi(trip) },
                  onDismissAiEnrichmentError = { viewModel.clearTripAiEnrichmentError(trip.id) },
                  onGenerateTravelGuide = { viewModel.generateTravelGuideForTrip(trip) },
                  onDismissGuideError = { viewModel.clearTripGuideError(trip.id) },
                  onAddSuggestedGuideItem = { name, category, reason ->
                    viewModel.addSuggestedGuideItem(trip.id, name, category, reason)
                  },
                  onDismissAttraction = { attraction ->
                    viewModel.dismissGuideAttraction(trip, attraction)
                  },
                  onCompleteAndResetTrip = { items, markCompleted ->
                    viewModel.completeAndResetTrip(trip, items, markCompleted)
                  },
                  onDeleteTrip = {
                    viewModel.deleteTrip(trip.id) {
                      navController.popBackStack()
                    }
                  },
                )
              } else {
                HomeTripsScreen(
                  tripsState = tripsState,
                  alwaysBringCount = alwaysBringCount,
                  alwaysDoCount = alwaysDoCount,
                  onPlanNewTrip = {
                    viewModel.resetPlannerDrafts()
                    navController.navigate("planner")
                  },
                  onOpenTrip = { id ->
                    viewModel.selectTrip(id)
                    navController.navigate("trip_detail/$id")
                  },
                  onOpenAlwaysBring = { navController.navigate("always_bring") },
                  onOpenGoogleTasksImport = {
                    openTasksSheetFor(null, null, TasksImportTarget.ALWAYS_BRING)
                  },
                )
              }
            }
          }
        }
      }
    }
  }

  if (showGoogleTasksSheet) {
    GoogleTasksImportSheet(
      viewModel = viewModel,
      activeTripId = sheetTripId,
      activeTripName = sheetTripName,
      defaultTarget = sheetDefaultTarget,
      initialSharedText = sheetSharedText,
      onDismiss = { showGoogleTasksSheet = false },
    )
  }
}
