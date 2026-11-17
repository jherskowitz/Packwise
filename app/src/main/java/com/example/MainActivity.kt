package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.AiChecklistGenerator
import com.example.data.firebase.PackingRepository
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.local.DeviceCalendarReader
import com.example.data.model.UiState
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
import com.example.ui.viewmodel.GoogleWorkspacePendingAction
import com.example.ui.viewmodel.PackWiseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_HOME = "home"
const val ROUTE_ALWAYS_BRING = "always_bring"
const val ROUTE_INTEGRATIONS = "integrations"
const val ROUTE_PLANNER = "planner"
const val ROUTE_TRIP_DETAIL = "trip_detail/{tripId}"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // STEP 1: Mandatory App Check configuration before UI, ViewModels, or Firebase operations.
    configureAppCheck(this, intent)

    WeatherNotificationHelper.ensureChannel(this)
    WeatherAlertWorker.schedulePeriodic(this)

    val launchTripId = intent?.getStringExtra(WeatherNotificationHelper.EXTRA_TRIP_ID)
    val sharedTasksText = if (
      intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true
    ) {
      intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
    } else null

    enableEdgeToEdge()
    setContent {
      PackWiseTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          AppNavigation(
            initialTripId = launchTripId,
            initialSharedTasksText = sharedTasksText,
          )
        }
      }
    }
  }
}

@Composable
fun AppNavigation(
  initialTripId: String? = null,
  initialSharedTasksText: String? = null,
) {
  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      PackWiseAppShell(
        currentUserId = user.uid,
        initialTripId = initialTripId,
        initialSharedTasksText = initialSharedTasksText,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackWiseAppShell(
  currentUserId: String,
  initialTripId: String?,
  initialSharedTasksText: String? = null,
) {
  val context = LocalContext.current
  val viewModel: PackWiseViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        PackWiseViewModel(
          repository = PackingRepository(db, Firebase.auth),
          weatherRepository = WeatherRepository(),
          aiChecklistGenerator = AiChecklistGenerator(),
          workspaceRepository = GoogleWorkspaceRepository(),
          deviceCalendarReader = DeviceCalendarReader(app.applicationContext),
          currentUserId = currentUserId,
        )
      }
    },
  )

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
  val alwaysBringState by viewModel.alwaysBringState.collectAsStateWithLifecycle()
  val selectedTripItemsState by viewModel.selectedTripItemsState.collectAsStateWithLifecycle()
  val selectedTripId by viewModel.selectedTripId.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
  val pendingConsent by viewModel.pendingConsentIntent.collectAsStateWithLifecycle()

  var showGoogleTasksSheet by remember { mutableStateOf(false) }
  var tasksSheetDefaultAlwaysBring by remember { mutableStateOf(false) }
  var pendingSharedTasksText by remember(initialSharedTasksText) { mutableStateOf(initialSharedTasksText) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(initialSharedTasksText) {
    if (!initialSharedTasksText.isNullOrBlank()) {
      pendingSharedTasksText = initialSharedTasksText
      tasksSheetDefaultAlwaysBring = false
      showGoogleTasksSheet = true
    }
  }

  // Handle Google OAuth consent resolution for Google Tasks / Google Calendar
  var activePendingAction by remember { mutableStateOf<GoogleWorkspacePendingAction?>(null) }
  val consentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val act = context as? Activity
    val action = activePendingAction
    if (act != null && action != null) {
      viewModel.handleWorkspaceConsentResult(act, action, result.resultCode, result.data)
    }
    activePendingAction = null
  }

  LaunchedEffect(pendingConsent) {
    val pair = pendingConsent ?: return@LaunchedEffect
    activePendingAction = pair.first
    consentLauncher.launch(IntentSenderRequest.Builder(pair.second.intentSender).build())
  }

  LaunchedEffect(toastMessage) {
    val msg = toastMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearToast()
  }

  LaunchedEffect(initialTripId) {
    if (!initialTripId.isNullOrBlank()) {
      viewModel.selectTrip(initialTripId)
      navController.navigate("trip_detail/$initialTripId")
    }
  }

  val trips = (tripsState as? UiState.Success)?.data.orEmpty()
  val alwaysBringCount = (alwaysBringState as? UiState.Success)?.data?.size ?: 0
  val activeTripForSheet = trips.firstOrNull { it.id == selectedTripId }
    ?: trips.firstOrNull { it.status == "active" }

  val isTopLevelRoute = currentRoute in setOf(ROUTE_HOME, ROUTE_ALWAYS_BRING, ROUTE_INTEGRATIONS)

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavRail && isTopLevelRoute) {
        NavigationRail {
          NavigationRailItem(
            selected = currentRoute == ROUTE_HOME,
            onClick = {
              navController.navigate(ROUTE_HOME) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            },
            icon = {
              Icon(
                if (currentRoute == ROUTE_HOME) Icons.Rounded.Luggage else Icons.Outlined.Luggage,
                contentDescription = "Trips",
              )
            },
            label = { Text("Trips") },
          )
          NavigationRailItem(
            selected = currentRoute == ROUTE_ALWAYS_BRING,
            onClick = {
              navController.navigate(ROUTE_ALWAYS_BRING) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            },
            icon = {
              Icon(
                if (currentRoute == ROUTE_ALWAYS_BRING) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                contentDescription = "Always Bring",
              )
            },
            label = { Text("Always Bring") },
          )
          NavigationRailItem(
            selected = currentRoute == ROUTE_INTEGRATIONS,
            onClick = {
              navController.navigate(ROUTE_INTEGRATIONS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            },
            icon = {
              Icon(
                if (currentRoute == ROUTE_INTEGRATIONS) Icons.Rounded.Settings else Icons.Outlined.Settings,
                contentDescription = "Integrations",
              )
            },
            label = { Text("Sync") },
          )
        }
      }

      Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
          if (isTopLevelRoute) {
            CenterAlignedTopAppBar(
              title = {
                Text(
                  text = when (currentRoute) {
                    ROUTE_ALWAYS_BRING -> "Always Bring"
                    ROUTE_INTEGRATIONS -> "Sync & Alerts"
                    else -> "PackWise"
                  },
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
              },
            )
          }
        },
        floatingActionButton = {
          if (currentRoute == ROUTE_HOME) {
            FloatingActionButton(
              onClick = { navController.navigate(ROUTE_PLANNER) },
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
              modifier = Modifier.testTag("home_plan_trip_fab"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = "Plan New Trip")
            }
          }
        },
        bottomBar = {
          if (!useNavRail && isTopLevelRoute) {
            NavigationBar {
              NavigationBarItem(
                selected = currentRoute == ROUTE_HOME,
                onClick = {
                  navController.navigate(ROUTE_HOME) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_HOME) Icons.Rounded.Luggage else Icons.Outlined.Luggage,
                    contentDescription = "Trips",
                  )
                },
                label = { Text("Trips") },
                modifier = Modifier.testTag("nav_trips"),
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_ALWAYS_BRING,
                onClick = {
                  navController.navigate(ROUTE_ALWAYS_BRING) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_ALWAYS_BRING) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                    contentDescription = "Always Bring",
                  )
                },
                label = { Text("Always Bring") },
                modifier = Modifier.testTag("nav_always_bring"),
              )
              NavigationBarItem(
                selected = currentRoute == ROUTE_INTEGRATIONS,
                onClick = {
                  navController.navigate(ROUTE_INTEGRATIONS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    if (currentRoute == ROUTE_INTEGRATIONS) Icons.Rounded.Settings else Icons.Outlined.Settings,
                    contentDescription = "Sync & Alerts",
                  )
                },
                label = { Text("Sync") },
                modifier = Modifier.testTag("nav_integrations"),
              )
            }
          }
        },
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = ROUTE_HOME,
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        ) {
          composable(ROUTE_HOME) {
            HomeTripsScreen(
              tripsState = tripsState,
              alwaysBringCount = alwaysBringCount,
              onPlanNewTrip = { navController.navigate(ROUTE_PLANNER) },
              onOpenTrip = { tripId ->
                viewModel.selectTrip(tripId)
                navController.navigate("trip_detail/$tripId")
              },
              onOpenAlwaysBring = {
                navController.navigate(ROUTE_ALWAYS_BRING)
              },
              onOpenGoogleTasksImport = {
                tasksSheetDefaultAlwaysBring = activeTripForSheet == null
                showGoogleTasksSheet = true
              },
            )
          }

          composable(ROUTE_ALWAYS_BRING) {
            AlwaysBringScreen(
              alwaysBringState = alwaysBringState,
              viewModel = viewModel,
              onOpenGoogleTasksImport = {
                tasksSheetDefaultAlwaysBring = true
                showGoogleTasksSheet = true
              },
            )
          }

          composable(ROUTE_INTEGRATIONS) {
            IntegrationsSettingsScreen(
              onOpenGoogleTasksImport = {
                tasksSheetDefaultAlwaysBring = activeTripForSheet == null
                showGoogleTasksSheet = true
              },
              onShowFeedback = { viewModel.showToast(it) },
            )
          }

          composable(ROUTE_PLANNER) {
            TripPlannerScreen(
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() },
              onTripCreated = { newTripId ->
                navController.navigate("trip_detail/$newTripId") {
                  popUpTo(ROUTE_PLANNER) { inclusive = true }
                }
              },
            )
          }

          composable(ROUTE_TRIP_DETAIL) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId").orEmpty()
            LaunchedEffect(tripId) {
              viewModel.selectTrip(tripId)
            }
            val trip = trips.firstOrNull { it.id == tripId }
            if (trip != null) {
              TripDetailScreen(
                trip = trip,
                itemsState = selectedTripItemsState,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onOpenGoogleTasksImport = {
                  tasksSheetDefaultAlwaysBring = false
                  showGoogleTasksSheet = true
                },
              )
            }
          }
        }
      }
    }
  }

  if (showGoogleTasksSheet) {
    GoogleTasksImportSheet(
      viewModel = viewModel,
      activeTripId = activeTripForSheet?.id,
      activeTripName = activeTripForSheet?.destination,
      defaultToAlwaysBring = tasksSheetDefaultAlwaysBring,
      initialSharedText = pendingSharedTasksText,
      onDismiss = {
        showGoogleTasksSheet = false
        pendingSharedTasksText = null
      },
    )
  }
}
