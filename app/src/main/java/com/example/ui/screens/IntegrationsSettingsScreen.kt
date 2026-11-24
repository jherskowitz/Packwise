package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.data.ai.NanoAvailabilityStatus
import com.example.data.firebase.signOutUser
import com.example.data.local.HomeCountryPreference
import com.example.data.local.HomeCountrySource
import com.example.notifications.WeatherAlertWorker
import com.example.notifications.WeatherNotificationHelper
import com.example.ui.components.HomeCountryPickerDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun IntegrationsSettingsScreen(
  nanoStatus: NanoAvailabilityStatus,
  nanoDownloadedMb: Long?,
  homeCountry: HomeCountryPreference,
  onSetManualHomeCountry: (String, String) -> Unit,
  onResetHomeCountryToDeviceRegion: () -> Unit,
  onDetectHomeCountryFromLocation: () -> Unit,
  onPrepareGeminiNano: () -> Unit,
  onRefreshNanoStatus: () -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
  onShowFeedback: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val user = Firebase.auth.currentUser
  var showCountryPicker by remember { mutableStateOf(false) }

  var hasNotificationPerm by remember {
    mutableStateOf(WeatherNotificationHelper.hasNotificationPermission(context))
  }
  var hasCalendarPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var hasCoarseLocationPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val notifPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasNotificationPerm = granted
    if (granted) {
      WeatherAlertWorker.schedulePeriodic(context)
      WeatherAlertWorker.triggerImmediateCheck(context)
      onShowFeedback("Real-time weather packing notifications enabled!")
    }
  }

  val calendarPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasCalendarPerm = granted
    if (granted) {
      onShowFeedback("Calendar access enabled for trip packing suggestions.")
    }
  }

  val locationPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasCoarseLocationPerm = granted
    if (granted) {
      onDetectHomeCountryFromLocation()
    } else {
      onShowFeedback("Location permission declined. You can set your home country manually anytime.")
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 720.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          text = "Integrations & Alerts · v1.4",
          style = MaterialTheme.typography.headlineSmall,
        )
      }

      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .testTag("settings_home_country_card"),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "Home Base Country", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "${homeCountry.countryName} (${homeCountry.countryCode}) · ${homeCountry.source.label}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Adapters, eSIMs, and entry/currency tips are only recommended when your destination is outside your home country.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = { showCountryPicker = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_change_home_country_button"),
              ) {
                Text("Change Country", maxLines = 1)
              }
              OutlinedButton(
                onClick = {
                  if (hasCoarseLocationPerm) {
                    onDetectHomeCountryFromLocation()
                  } else {
                    locationPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                  }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("settings_detect_location_button"),
              ) {
                Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use Location", maxLines = 1)
              }
            }
          }
        }
      }

      item {
        IntegrationCard(
          icon = Icons.Rounded.NotificationsActive,
          title = "Weather Packing Notifications",
          description = if (hasNotificationPerm) {
            "Active — background weather checks run automatically and alert you before rain, cold, or heatwaves."
          } else {
            "Grant notification permission to receive real-time weather packing reminders."
          },
          buttonLabel = if (hasNotificationPerm) "Run Weather Check Now" else "Enable Weather Alerts",
          testTag = "settings_weather_alerts_button",
          onClick = {
            if (!hasNotificationPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              WeatherAlertWorker.schedulePeriodic(context)
              WeatherAlertWorker.triggerImmediateCheck(context)
              onShowFeedback("Live weather forecast check scheduled.")
            }
          },
        )
      }

      item {
        IntegrationCard(
          icon = Icons.Rounded.CalendarMonth,
          title = "Calendar Apps Sync",
          description = if (hasCalendarPerm) {
            "Connected to synced device calendars (Google Calendar, Outlook, etc.) for event-aware packing."
          } else {
            "Allow calendar read access so PackWise can inspect planned trip events and suggest outfits/gear."
          },
          buttonLabel = if (hasCalendarPerm) "Calendar Permission Granted" else "Allow Calendar Access",
          testTag = "settings_calendar_button",
          onClick = {
            if (!hasCalendarPerm) {
              calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
            } else {
              onShowFeedback("Device calendars are already connected.")
            }
          },
        )
      }

      item {
        val nanoDescription = when (nanoStatus) {
          NanoAvailabilityStatus.AVAILABLE ->
            "Active — Gemini Nano runs directly on-device via Android AICore for fast offline packing suggestions, with Cloud Gemini as fallback."
          NanoAvailabilityStatus.DOWNLOADABLE ->
            "Supported on this phone — download the on-device Gemini Nano model now so checklists generate locally without cloud round-trips."
          NanoAvailabilityStatus.DOWNLOADING -> {
            val mbLabel = nanoDownloadedMb?.let { " ($it MB downloaded)" }.orEmpty()
            "Downloading on-device Gemini Nano model$mbLabel. Cloud Gemini handles requests in the meantime."
          }
          NanoAvailabilityStatus.UNAVAILABLE ->
            "Not available on this device or emulator — PackWise automatically uses Cloud Gemini (Firebase AI Logic) for trip enrichment."
        }
        val nanoButtonLabel = when (nanoStatus) {
          NanoAvailabilityStatus.AVAILABLE -> "Gemini Nano Ready (Re-check)"
          NanoAvailabilityStatus.DOWNLOADABLE -> "Prepare Gemini Nano"
          NanoAvailabilityStatus.DOWNLOADING -> "Downloading..."
          NanoAvailabilityStatus.UNAVAILABLE -> "Check Device Support"
        }
        IntegrationCard(
          icon = Icons.Rounded.AutoAwesome,
          title = "On-Device AI (Gemini Nano)",
          description = nanoDescription,
          buttonLabel = nanoButtonLabel,
          testTag = "settings_gemini_nano_button",
          onClick = {
            when (nanoStatus) {
              NanoAvailabilityStatus.DOWNLOADABLE -> onPrepareGeminiNano()
              NanoAvailabilityStatus.AVAILABLE -> {
                onRefreshNanoStatus()
                onShowFeedback("On-device Gemini Nano is ready.")
              }
              NanoAvailabilityStatus.DOWNLOADING -> {
                onShowFeedback("Gemini Nano download is in progress.")
              }
              NanoAvailabilityStatus.UNAVAILABLE -> {
                onRefreshNanoStatus()
                onShowFeedback("Cloud Gemini fallback is active for this device.")
              }
            }
          },
        )
      }

      item {
        IntegrationCard(
          icon = Icons.Rounded.Checklist,
          title = "Google Tasks Import",
          description = "Connect your Google account or paste / share / upload a list from Google Tasks into any trip or Always Bring vault.",
          buttonLabel = "Import Google Tasks",
          testTag = "settings_google_tasks_button",
          onClick = onOpenGoogleTasksImport,
        )
      }

      item {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = user?.displayName ?: "Google Account",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = user?.email ?: "Signed in",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
              onClick = { signOutUser(credentialManager, scope) },
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
              ),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("sign_out_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign Out")
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }

  if (showCountryPicker) {
    HomeCountryPickerDialog(
      currentCountryCode = homeCountry.countryCode,
      onSelectCountry = onSetManualHomeCountry,
      onResetToDeviceRegion = onResetHomeCountryToDeviceRegion,
      onDismiss = { showCountryPicker = false },
    )
  }
}

@Composable
private fun IntegrationCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  description: String,
  buttonLabel: String,
  testTag: String,
  onClick: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(12.dp))
      Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag(testTag),
      ) {
        Text(buttonLabel)
      }
    }
  }
}
