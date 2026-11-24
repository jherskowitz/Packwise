package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.HomeBasePreferences
import com.example.data.model.UiState
import com.example.notifications.WeatherNotificationHelper
import com.example.ui.components.HomeCountryPickerDialog
import com.example.ui.viewmodel.PackWiseViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val SUGGESTED_ACTIVITIES = listOf(
  "City Walking",
  "Hiking",
  "Beach & Swim",
  "Business Meetings",
  "Evening Dining",
  "Workout / Run",
  "Photography",
  "Cold / Snow",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripPlannerScreen(
  viewModel: PackWiseViewModel,
  onNavigateBack: () -> Unit,
  onTripCreated: (String) -> Unit,
) {
  val context = LocalContext.current
  val destinationQuery by viewModel.destinationQuery.collectAsStateWithLifecycle()
  val placeSuggestions by viewModel.placeSuggestions.collectAsStateWithLifecycle()
  val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
  val startEpochDay by viewModel.startEpochDay.collectAsStateWithLifecycle()
  val endEpochDay by viewModel.endEpochDay.collectAsStateWithLifecycle()
  val selectedActivities by viewModel.selectedActivities.collectAsStateWithLifecycle()
  val weatherState by viewModel.weatherForecastState.collectAsStateWithLifecycle()
  val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
  val calendarStatusMessage by viewModel.calendarStatusMessage.collectAsStateWithLifecycle()
  val generationState by viewModel.plannerGenerationState.collectAsStateWithLifecycle()
  val homeCountry by viewModel.homeCountry.collectAsStateWithLifecycle()

  var showDateRangePicker by remember { mutableStateOf(false) }
  var showHomeCountryDialog by remember { mutableStateOf(false) }
  var customActivityText by remember { mutableStateOf("") }
  var weatherNotificationsEnabled by remember { mutableStateOf(true) }

  val calendarPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      viewModel.loadDeviceCalendarEvents()
    } else {
      viewModel.showToast("Calendar permission denied. You can still connect Google Calendar or continue without events.")
    }
  }

  val notifPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    weatherNotificationsEnabled = granted
    if (!granted) {
      viewModel.showToast("Notification permission not granted; live alerts will appear inside the trip screen.")
    }
  }

  val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
  val startDate = LocalDate.ofEpochDay(startEpochDay)
  val endDate = LocalDate.ofEpochDay(endEpochDay)
  val durationDays = ((endEpochDay - startEpochDay) + 1L).coerceAtLeast(1L)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Plan New Trip") },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("planner_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 760.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
              .fillMaxWidth()
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "1. Destination & Dates",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedTextField(
                value = destinationQuery,
                onValueChange = viewModel::onDestinationQueryChange,
                label = { Text("City or destination") },
                placeholder = { Text("e.g., Tokyo, Reykjavik, Lisbon") },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("planner_destination_input"),
              )

              if (placeSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                  shape = MaterialTheme.shapes.medium,
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                ) {
                  Column {
                    placeSuggestions.forEachIndexed { index, place ->
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .clickable { viewModel.selectDestinationPlace(place) }
                          .padding(horizontal = 14.dp, vertical = 12.dp)
                          .testTag("place_suggestion_$index"),
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
                          text = place.displayLabel(),
                          style = MaterialTheme.typography.bodyMedium,
                        )
                      }
                      if (index < placeSuggestions.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              val selectedIsIntl = selectedPlace?.country?.let {
                HomeBasePreferences.isTripInternational(homeCountry, it)
              }
              val homeHint = when (selectedIsIntl) {
                false -> "Home base: ${homeCountry.countryName} · Domestic trip (adapters & eSIM skipped)"
                true -> "Home base: ${homeCountry.countryName} · International (adapter & eSIM included)"
                null -> "Home base: ${homeCountry.countryName} · Adapters/eSIM only for int'l trips"
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = homeHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                TextButton(
                  onClick = { showHomeCountryDialog = true },
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("planner_change_home_button"),
                ) {
                  Text("Change", maxLines = 1, softWrap = false)
                }
              }

              Spacer(modifier = Modifier.height(4.dp))

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                  .clickable { showDateRangePicker = true }
                  .padding(14.dp)
                  .testTag("planner_dates_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    Icons.Rounded.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "${startDate.format(dateFmt)} – ${endDate.format(dateFmt)}",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                      text = "$durationDays day(s) · Tap to change dates",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Text(
                  text = "Edit",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
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
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "2. Live Weather Check",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                  )
                }
                TextButton(
                  onClick = { viewModel.refreshPlannerForecast() },
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("planner_refresh_weather_button"),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Check", maxLines = 1, softWrap = false)
                }
              }

              when (val ws = weatherState) {
                is UiState.Idle -> {
                  Text(
                    text = if (selectedPlace == null) {
                      "Select a destination above or tap Check to pull Open-Meteo daily temperatures and rain probabilities."
                    } else {
                      "Tap Check to fetch the latest forecast for ${selectedPlace?.name}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                is UiState.Loading -> {
                  Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Fetching live forecast...", style = MaterialTheme.typography.bodySmall)
                  }
                }
                is UiState.Error -> {
                  Text(
                    text = ws.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
                is UiState.Success -> {
                  val forecast = ws.data
                  Text(
                    text = forecast.summaryText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                  )
                  if (forecast.alertActive && forecast.alertMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.small)
                        .padding(10.dp),
                      verticalAlignment = Alignment.Top,
                    ) {
                      Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = forecast.alertMessage,
                        style = MaterialTheme.typography.bodySmall,
                      )
                    }
                  }
                }
              }
            }
          }
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
              Text(
                text = "3. Planned Activities",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Spacer(modifier = Modifier.height(6.dp))
              val allChoices = remember(selectedActivities) {
                (SUGGESTED_ACTIVITIES + selectedActivities).distinct()
              }
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                allChoices.forEach { activity ->
                  FilterChip(
                    selected = activity in selectedActivities,
                    onClick = { viewModel.toggleActivity(activity) },
                    label = { Text(activity) },
                  )
                }
              }
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                OutlinedTextField(
                  value = customActivityText,
                  onValueChange = { customActivityText = it },
                  label = { Text("Custom activity") },
                  placeholder = { Text("e.g., Scuba diving, Concert") },
                  singleLine = true,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("planner_custom_activity_input"),
                )
                OutlinedButton(
                  onClick = {
                    viewModel.addCustomActivity(customActivityText)
                    customActivityText = ""
                  },
                  enabled = customActivityText.isNotBlank(),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("planner_add_activity_button"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Add", maxLines = 1, softWrap = false)
                }
              }
            }
          }
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
                Icon(
                  Icons.Rounded.CalendarMonth,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "4. Calendar Context (Optional)",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Inspect events during your trip dates to auto-add event-specific outfits and gear.",
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
                    val hasPerm = ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.READ_CALENDAR,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                      viewModel.loadDeviceCalendarEvents()
                    } else {
                      calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                  },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("planner_device_calendar_button"),
                ) {
                  Text("Device Calendars", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                  onClick = {
                    (context as? Activity)?.let { viewModel.fetchGoogleCalendarEvents(it) }
                  },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("planner_google_calendar_button"),
                ) {
                  Text("Google Calendar", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
              }

              calendarStatusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              if (calendarEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                calendarEvents.take(6).forEach { ev ->
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                  ) {
                    Text(
                      text = "• ${ev.title}" + if (ev.location.isNotBlank()) " (${ev.location})" else "",
                      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    )
                    if (ev.suggestedItems.isNotEmpty()) {
                      Text(
                        text = "  Suggested gear: ${ev.suggestedItems.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                }
              }
            }
          }
        }

        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(MaterialTheme.shapes.large)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Weather Packing Alerts",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text = "Notify before rain, cold snaps, or heatwaves",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = weatherNotificationsEnabled,
              onCheckedChange = { enabled ->
                if (enabled &&
                  !WeatherNotificationHelper.hasNotificationPermission(context) &&
                  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                  notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  weatherNotificationsEnabled = enabled
                }
              },
              modifier = Modifier.testTag("planner_notifications_switch"),
            )
          }
        }

        if (generationState is UiState.Error) {
          item {
            val errMsg = (generationState as UiState.Error).message
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("planner_error_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.Top,
                ) {
                  Text(
                    text = errMsg,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                  )
                  TextButton(
                    onClick = { viewModel.clearPlannerError() },
                    modifier = Modifier.testTag("planner_error_dismiss_button"),
                  ) {
                    Text("Dismiss", maxLines = 1, softWrap = false)
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = {
                      viewModel.createTripWithoutAi(
                        weatherNotificationsEnabled = weatherNotificationsEnabled,
                        onCreated = onTripCreated,
                      )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("planner_error_skip_ai_button"),
                  ) {
                    Text("Skip AI", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                  }
                  Button(
                    onClick = {
                      viewModel.generateAndCreateTrip(
                        weatherNotificationsEnabled = weatherNotificationsEnabled,
                        onCreated = onTripCreated,
                      )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("planner_error_retry_button"),
                  ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry AI", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
        }

        item {
          val isGenerating = generationState is UiState.Loading
          Button(
            onClick = {
              viewModel.generateAndCreateTrip(
                weatherNotificationsEnabled = weatherNotificationsEnabled,
                onCreated = onTripCreated,
              )
            },
            enabled = !isGenerating && destinationQuery.isNotBlank(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("planner_generate_button"),
          ) {
            if (isGenerating) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Creating Trip Checklist...")
            } else {
              Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Generate AI Packing Checklist", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
      }
    }
  }

  if (showDateRangePicker) {
    val initialStartMs = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val initialEndMs = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDateRangePickerState(
      initialSelectedStartDateMillis = initialStartMs,
      initialSelectedEndDateMillis = initialEndMs,
    )
    DatePickerDialog(
      onDismissRequest = { showDateRangePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val sMs = pickerState.selectedStartDateMillis
            val eMs = pickerState.selectedEndDateMillis ?: sMs
            if (sMs != null && eMs != null) {
              val sDay = Instant.ofEpochMilli(sMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
              val eDay = Instant.ofEpochMilli(eMs).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
              viewModel.updateTripDates(sDay, eDay)
            }
            showDateRangePicker = false
          },
        ) {
          Text("Apply")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDateRangePicker = false }) {
          Text("Cancel")
        }
      },
    ) {
      DateRangePicker(
        state = pickerState,
        modifier = Modifier.heightIn(max = 460.dp),
      )
    }
  }

  if (showHomeCountryDialog) {
    HomeCountryPickerDialog(
      currentCountryCode = homeCountry.countryCode,
      onSelectCountry = viewModel::setManualHomeCountry,
      onResetToDeviceRegion = viewModel::resetHomeCountryToDeviceRegion,
      onDismiss = { showHomeCountryDialog = false },
    )
  }
}
