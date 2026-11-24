package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Trip
import com.example.data.model.UiState
import com.example.ui.components.DestinationHeaderImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeTripsScreen(
  tripsState: UiState<List<Trip>>,
  alwaysBringCount: Int,
  alwaysDoCount: Int,
  onPlanNewTrip: () -> Unit,
  onOpenTrip: (String) -> Unit,
  onOpenAlwaysBring: () -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize(),
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
        HeroTripHeaderCard(
          alwaysBringCount = alwaysBringCount,
          alwaysDoCount = alwaysDoCount,
          onPlanNewTrip = onPlanNewTrip,
          onOpenAlwaysBring = onOpenAlwaysBring,
          onOpenGoogleTasksImport = onOpenGoogleTasksImport,
        )
      }

      item {
        Text(
          text = "Your Trips",
          style = MaterialTheme.typography.titleLarge,
        )
      }

      when (tripsState) {
        is UiState.Loading, is UiState.Idle -> {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
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
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          val trips = tripsState.data
          if (trips.isEmpty()) {
            item {
              EmptyTripsCard(onPlanNewTrip = onPlanNewTrip)
            }
          } else {
            items(trips, key = { it.id }) { trip ->
              TripOverviewCard(
                trip = trip,
                onClick = { onOpenTrip(trip.id) },
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(76.dp)) }
    }
  }
}

@Composable
private fun HeroTripHeaderCard(
  alwaysBringCount: Int,
  alwaysDoCount: Int,
  onPlanNewTrip: () -> Unit,
  onOpenAlwaysBring: () -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(155.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_hero_journey),
          contentDescription = "PackWise trip banner",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(Color(0x22062529), Color(0xCC062529)),
              )
            ),
        )
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp),
        ) {
          Text(
            text = "Ready for Takeoff",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFCDE9EC),
          )
          Text(
            text = "AI Packing Built Around Weather & Calendar",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
          )
        }
      }

      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "Your vault holds $alwaysBringCount Always Bring item(s) and $alwaysDoCount Always-Do task(s) added fresh to every trip.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = onPlanNewTrip,
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("hero_plan_trip_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Plan New Trip Checklist", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onOpenAlwaysBring,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("home_always_bring_button"),
            ) {
              Text("Always Vault", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = onOpenGoogleTasksImport,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("home_import_tasks_button"),
            ) {
              Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Import Tasks", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
          }
        }
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
      Icon(
        imageVector = Icons.Rounded.FlightTakeoff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
      )
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "No trips planned yet",
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Pick a destination, check live weather, connect activities or calendar events, and generate a tailored checklist.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(14.dp))
      Button(
        onClick = onPlanNewTrip,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("empty_plan_trip_button"),
      ) {
        Text("Create First Trip", maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun TripOverviewCard(
  trip: Trip,
  onClick: () -> Unit,
) {
  val dateFmt = DateTimeFormatter.ofPattern("MMM d")
  val startLabel = LocalDate.ofEpochDay(trip.startDateEpochDay).format(dateFmt)
  val endLabel = LocalDate.ofEpochDay(trip.endDateEpochDay).format(dateFmt)

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.large)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
      .clickable(onClick = onClick)
      .testTag("trip_card_${trip.id}"),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(116.dp),
      ) {
        DestinationHeaderImage(
          headerImageUrl = trip.headerImageUrl,
          destination = trip.destination,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(Color(0x22062529), Color(0xD9062529)),
              )
            ),
        )
        Surface(
          shape = MaterialTheme.shapes.small,
          color = if (trip.status == "completed") {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
          } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
          },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp),
        ) {
          Text(
            text = if (trip.status == "completed") "Reset Fresh" else "Active",
            style = MaterialTheme.typography.labelSmall,
            color = if (trip.status == "completed") {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          Text(
            text = if (trip.country.isNotBlank()) "${trip.destination}, ${trip.country}" else trip.destination,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$startLabel – $endLabel · ${trip.durationDays} day(s)",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFCDE9EC),
          )
        }
      }

      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (trip.weatherAlertActive) Icons.Rounded.WarningAmber else Icons.Rounded.WbSunny,
            contentDescription = null,
            tint = if (trip.weatherAlertActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = trip.weatherSummary.ifBlank { "Live weather ready" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (trip.weatherAlertActive && trip.weatherAlertMessage.isNotBlank()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = trip.weatherAlertMessage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (trip.resetCount > 0L) {
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Rounded.Refresh,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Reset fresh ${trip.resetCount} time(s)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
