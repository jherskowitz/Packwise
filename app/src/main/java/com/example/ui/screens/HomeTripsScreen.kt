package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Trip
import com.example.data.model.UiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeTripsScreen(
  tripsState: UiState<List<Trip>>,
  alwaysBringCount: Int,
  onPlanNewTrip: () -> Unit,
  onOpenTrip: (String) -> Unit,
  onOpenAlwaysBring: () -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
) {
  var showCompletedTrips by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        HeroSummaryCard(
          alwaysBringCount = alwaysBringCount,
          onPlanNewTrip = onPlanNewTrip,
          onOpenAlwaysBring = onOpenAlwaysBring,
          onOpenGoogleTasksImport = onOpenGoogleTasksImport,
        )
      }

      when (tripsState) {
        is UiState.Loading -> {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }
        is UiState.Error -> {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = tripsState.message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          val activeTrips = tripsState.data.filter { it.status == "active" }
          val completedTrips = tripsState.data.filter { it.status == "completed" }

          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Active Journeys (${activeTrips.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
              if (activeTrips.isNotEmpty()) {
                Text(
                  text = "Tap to pack",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }

          if (activeTrips.isEmpty()) {
            item {
              EmptyTripsCard(onPlanNewTrip = onPlanNewTrip)
            }
          } else {
            items(activeTrips, key = { it.id }) { trip ->
              TripCard(
                trip = trip,
                onClick = { onOpenTrip(trip.id) },
              )
            }
          }

          if (completedTrips.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { showCompletedTrips = !showCompletedTrips }
                  .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Completed & Reset Journeys (${completedTrips.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(
                  imageVector = if (showCompletedTrips) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                  contentDescription = "Toggle completed trips",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            item {
              AnimatedVisibility(visible = showCompletedTrips) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  completedTrips.forEach { trip ->
                    TripCard(
                      trip = trip,
                      onClick = { onOpenTrip(trip.id) },
                    )
                  }
                }
              }
            }
          }
        }
        is UiState.Idle -> Unit
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }
}

@Composable
private fun HeroSummaryCard(
  alwaysBringCount: Int,
  onPlanNewTrip: () -> Unit,
  onOpenAlwaysBring: () -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(145.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_hero_journey),
          contentDescription = "PackWise expedition banner",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.scrim.copy(alpha = 0.08f),
                  MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                )
              )
            )
        )
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp),
        ) {
          Text(
            text = "Ready for Takeoff",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Text(
            text = "$alwaysBringCount Always Bring essentials synced & auto-resetting",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = onPlanNewTrip,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("hero_plan_trip_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Plan Trip", maxLines = 1)
        }
        OutlinedButton(
          onClick = onOpenGoogleTasksImport,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("hero_import_tasks_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Google Tasks", maxLines = 1)
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onOpenAlwaysBring)
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .testTag("hero_always_bring_banner"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Manage Always Bring Essentials ($alwaysBringCount)",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Edit Vault →",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun EmptyTripsCard(
  onPlanNewTrip: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.FlightTakeoff,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(28.dp),
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = "No Active Trips Yet",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Enter a destination, dates, and activities. PackWise fetches live weather, reads your calendar events, and generates a tailored checklist.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = onPlanNewTrip,
        modifier = Modifier
          .height(48.dp)
          .testTag("empty_state_plan_trip_button"),
      ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Generate My First Checklist")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripCard(
  trip: Trip,
  onClick: () -> Unit,
) {
  val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
  val startLabel = remember(trip.startDateEpochDay) {
    LocalDate.ofEpochDay(trip.startDateEpochDay).format(dateFormatter)
  }
  val endLabel = remember(trip.endDateEpochDay) {
    LocalDate.ofEpochDay(trip.endDateEpochDay).format(dateFormatter)
  }

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
      .clip(MaterialTheme.shapes.large)
      .clickable(onClick = onClick)
      .testTag("trip_card_${trip.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = if (trip.country.isNotBlank()) "${trip.destination}, ${trip.country}" else trip.destination,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "$startLabel – $endLabel · ${trip.durationDays} day(s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = if (trip.status == "completed") {
            MaterialTheme.colorScheme.tertiaryContainer
          } else {
            MaterialTheme.colorScheme.primaryContainer
          },
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (trip.status == "completed") Icons.Rounded.CheckCircle else Icons.Rounded.FlightTakeoff,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = if (trip.status == "completed") {
                MaterialTheme.colorScheme.onTertiaryContainer
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer
              },
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (trip.status == "completed") "Reset Fresh (${trip.resetCount})" else "Active",
              style = MaterialTheme.typography.labelSmall,
              color = if (trip.status == "completed") {
                MaterialTheme.colorScheme.onTertiaryContainer
              } else {
                MaterialTheme.colorScheme.onPrimaryContainer
              },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .background(
            if (trip.weatherAlertActive) {
              MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            } else {
              MaterialTheme.colorScheme.surfaceContainer
            }
          )
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = if (trip.weatherAlertActive) Icons.Rounded.WarningAmber else Icons.Rounded.CloudQueue,
          contentDescription = null,
          tint = if (trip.weatherAlertActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (trip.weatherAlertActive && trip.weatherAlertMessage.isNotBlank()) {
            trip.weatherAlertMessage
          } else {
            trip.weatherSummary.ifBlank { "Tap to refresh weather forecast" }
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      if (trip.activities.isNotEmpty() || trip.calendarEventsSummary.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          trip.activities.take(4).forEach { act ->
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Text(
                text = act,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
          if (trip.calendarEventsSummary.isNotEmpty()) {
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
              Text(
                text = "${trip.calendarEventsSummary.size} Calendar Event(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
        }
      }
    }
  }
}
