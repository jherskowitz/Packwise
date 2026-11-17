package com.example.ui.screens

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UiState
import com.example.notifications.WeatherNotificationHelper
import com.example.ui.viewmodel.PackWiseViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SUGGESTED_ACTIVITIES = listOf(
  "City Walking",
  "Hiking",
  "Beach & Swim",
  "Business / Formal",
  "Dining Out",
  "Photography",
  "Fitness / Gym",
  "Skiing / Snow",
  "Camping",
  "Nightlife",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripPlannerScreen(
  viewModel: PackWiseViewModel,
  onNavigateBack: () -> Unit,
  onTripCreated: (String) -> Unit,
) {
  BackHandler(onBack = onNavigateBack)

  val context = LocalContext.current
  val destinationQuery by viewModel.destinationQuery.collectAsStateWithLifecycle()
  val placeSuggestions by viewModel.placeSuggestions.collectAsStateWithLifecycle()
  val startEpochDay by viewModel.startEpochDay.collectAsStateWithLifecycle()
  val endEpochDay by viewModel.endEpochDay.collectAsStateWithLifecycle()
  val selectedActivities by viewModel.selectedActivities.collectAsStateWithLifecycle()
  val weatherState by viewModel.weatherForecastState.collectAsStateWithLifecycle()
  val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
  val calendarStatus by viewModel.calendarStatusMessage.collectAsStateWithLifecycle()
  val generationState by viewModel.plannerGenerationState.collectAsStateWithLifecycle()

  var customActivityText by remember { mutableStateOf("") }
  var showDateRangeDialog by remember { mutableStateOf(false) }
  var enableWeatherAlerts by remember {
    mutableStateOf(WeatherNotificationHelper.hasNotificationPermission(context))
  }

  val calendarPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      viewModel.loadDeviceCalendarEvents()
    } else {
      viewModel.showToast("Calendar permission denied. You can still sync Google Calendar directly.")
    }
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    enableWeatherAlerts = granted
  }

  val durationDays = (endEpochDay - startEpochDay + 1L).coerceAtLeast(1L)
  val formatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
  val startFormatted = remember(startEpochDay) { LocalDate.ofEpochDay(startEpochDay).format(formatter) }
  val endFormatted = remember(endEpochDay) { LocalDate.ofEpochDay(endEpochDay).format(formatter) }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Plan & Generate Checklist") },
      navigationIcon = {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.testTag("planner_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
      },
    )

    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Banner
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_weather_pack),
              contentDescription = "Weather and destination planner",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
            )
          }
        }

        // 1. Destination
        item {
          SectionCard(title = "1. Destination & Dates", icon = Icons.Rounded.LocationOn) {
            OutlinedTextField(
              value = destinationQuery,
              onValueChange = { viewModel.onDestinationQueryChange(it) },
              label = { Text("Where are you traveling?") },
              placeholder = { Text("e.g., Tokyo, Reykjavik, Maui, Paris") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("destination_input"),
            )

            if (placeSuggestions.isNotEmpty()) {
              Spacer(modifier = Modifier.height(8.dp))
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .background(MaterialTheme.colorScheme.surfaceContainer)
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
              ) {
                placeSuggestions.forEach { place ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { viewModel.selectDestinationPlace(place) }
                      .padding(horizontal = 14.dp, vertical = 12.dp)
                      .testTag("place_suggestion_${place.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      Icons.Rounded.LocationOn,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = place.displayLabel,
                      style = MaterialTheme.typography.bodyMedium,
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
              onClick = { showDateRangeDialog = true },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("date_range_button"),
            ) {
              Icon(Icons.Rounded.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("$startFormatted → $endFormatted ($durationDays days)")
            }
          }
        }

        // 2. Live Weather Preview
        item {
          SectionCard(title = "2. Live Destination Weather", icon = Icons.Rounded.CloudQueue) {
            when (val w = weatherState) {
              is UiState.Idle -> {
                Text(
                  text = "Select a destination from search suggestions to load its live 7–14 day forecast & weather packing alerts.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              is UiState.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                  Spacer(modifier = Modifier.width(10.dp))
                  Text("Fetching Open-Meteo forecast...", style = MaterialTheme.typography.bodyMedium)
                }
              }
              is UiState.Error -> {
                Text(
                  text = w.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                )
              }
              is UiState.Success -> {
                val summary = w.data
                Text(
                  text = summary.summaryText,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
                if (summary.alertActive && summary.alertMessage.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                  ) {
                    Text(
                      text = summary.alertMessage,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(10.dp),
                    )
                  }
                }
              }
            }
          }
        }

        // 3. Activities
        item {
          SectionCard(title = "3. Destination Activities", icon = Icons.Rounded.AutoAwesome) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val allChips = (SUGGESTED_ACTIVITIES + selectedActivities).distinct()
              allChips.forEach { activity ->
                val selected = activity in selectedActivities
                FilterChip(
                  selected = selected,
                  onClick = { viewModel.toggleActivity(activity) },
                  label = { Text(activity) },
                  modifier = Modifier.testTag("activity_chip_$activity"),
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              OutlinedTextField(
                value = customActivityText,
                onValueChange = { customActivityText = it },
                label = { Text("Add custom activity") },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("custom_activity_input"),
              )
              Spacer(modifier = Modifier.width(8.dp))
              OutlinedButton(
                onClick = {
                  viewModel.addCustomActivity(customActivityText)
                  customActivityText = ""
                },
                modifier = Modifier
                  .height(52.dp)
                  .testTag("add_custom_activity_button"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add activity")
              }
            }
          }
        }

        // 4. Calendar Integration
        item {
          SectionCard(title = "4. Calendar Events Integration", icon = Icons.Rounded.CalendarMonth) {
            Text(
              text = "Pull planned events during your trip dates to automatically suggest event-specific outfits and gear.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = {
                  calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("sync_device_calendar_button"),
              ) {
                Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Device Calendars", maxLines = 1)
              }
              OutlinedButton(
                onClick = {
                  (context as? Activity)?.let { viewModel.fetchGoogleCalendarEvents(it) }
                },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("sync_google_calendar_button"),
              ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Google Calendar", maxLines = 1)
              }
            }
            if (calendarStatus != null) {
              val statusText = calendarStatus!!
              val isErrorStatus = statusText.contains("error", ignoreCase = true) ||
                statusText.contains("blocked", ignoreCase = true) ||
                statusText.contains("cancelled", ignoreCase = true)
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isErrorStatus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              )
            }
            if (calendarEvents.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                calendarEvents.take(5).forEach { ev ->
                  Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                      Text(
                        text = "${ev.title} · ${ev.sourceApp}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                      )
                      if (ev.suggestedItems.isNotEmpty()) {
                        Text(
                          text = "Suggests: ${ev.suggestedItems.joinToString { it.name }}",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 5. Real-time Weather Push Notifications Toggle
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  Icons.Rounded.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Real-Time Weather Alerts",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  Text(
                    text = "Notify when rain, cold, or heat shifts your packing needs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Switch(
                checked = enableWeatherAlerts,
                onCheckedChange = { wantEnable ->
                  if (wantEnable && !WeatherNotificationHelper.hasNotificationPermission(context)) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                      enableWeatherAlerts = true
                    }
                  } else {
                    enableWeatherAlerts = wantEnable
                  }
                },
                modifier = Modifier.testTag("weather_alerts_switch"),
              )
            }
          }
        }

        // Error if any
        if (generationState is UiState.Error) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = (generationState as UiState.Error).message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
        }

        // Generate CTA
        item {
          val isGenerating = generationState is UiState.Loading
          Button(
            onClick = {
              viewModel.clearPlannerError()
              viewModel.generateAndCreateTrip(
                weatherNotificationsEnabled = enableWeatherAlerts,
                onCreated = onTripCreated,
              )
            },
            enabled = !isGenerating && destinationQuery.isNotBlank(),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("generate_checklist_button"),
          ) {
            if (isGenerating) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Generating tailored checklist...")
            } else {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Generate Custom Packing List")
            }
          }
          Spacer(modifier = Modifier.height(24.dp))
        }
      }
    }
  }

  if (showDateRangeDialog) {
    val startMillis = LocalDate.ofEpochDay(startEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val endMillis = LocalDate.ofEpochDay(endEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val pickerState = rememberDateRangePickerState(
      initialSelectedStartDateMillis = startMillis,
      initialSelectedEndDateMillis = endMillis,
    )
    DatePickerDialog(
      onDismissRequest = { showDateRangeDialog = false },
      confirmButton = {
        TextButton(
          onClick = {
            val s = pickerState.selectedStartDateMillis?.let {
              Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            } ?: startEpochDay
            val e = pickerState.selectedEndDateMillis?.let {
              Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            } ?: s
            viewModel.updateTripDates(s, e)
            showDateRangeDialog = false
          },
        ) {
          Text("Confirm")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDateRangeDialog = false }) { Text("Cancel") }
      },
    ) {
      DateRangePicker(
        state = pickerState,
        modifier = Modifier.height(420.dp),
      )
    }
  }
}

@Composable
private fun SectionCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  content: @Composable () -> Unit,
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
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      content()
    }
  }
}
