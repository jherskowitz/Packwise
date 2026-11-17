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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
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
import com.example.data.firebase.signOutUser
import com.example.notifications.WeatherAlertWorker
import com.example.notifications.WeatherNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun IntegrationsSettingsScreen(
  onOpenGoogleTasksImport: () -> Unit,
  onShowFeedback: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val user = Firebase.auth.currentUser

  var hasNotificationPerm by remember {
    mutableStateOf(WeatherNotificationHelper.hasNotificationPermission(context))
  }
  var hasCalendarPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
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
          text = "Integrations & Alerts · v1.1",
          style = MaterialTheme.typography.headlineSmall,
        )
      }

      // Weather Notifications Card
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

      // Calendar Integration Card
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

      // Google Tasks Card
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

      // Account Card
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
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
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
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag(testTag),
      ) {
        Text(buttonLabel)
      }
    }
  }
}
