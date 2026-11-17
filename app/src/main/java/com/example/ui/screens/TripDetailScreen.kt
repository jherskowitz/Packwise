package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.PackingItem
import com.example.data.model.Trip
import com.example.data.model.UiState
import com.example.notifications.WeatherNotificationHelper
import com.example.ui.viewmodel.PackWiseViewModel

private enum class ChecklistFilter(val label: String) {
  ALL("All"),
  UNPACKED("To Pack"),
  ALWAYS_BRING("Always Bring"),
  TRIP_CONTEXT("Trip Context"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(
  trip: Trip,
  itemsState: UiState<List<PackingItem>>,
  viewModel: PackWiseViewModel,
  onNavigateBack: () -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
) {
  BackHandler(onBack = onNavigateBack)

  val context = LocalContext.current
  var selectedFilter by remember { mutableStateOf(ChecklistFilter.ALL) }
  var showAddItemDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }

  val notificationLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      viewModel.toggleTripWeatherNotifications(trip, true)
      viewModel.refreshLiveWeatherForTrip(trip) { dest, msg ->
        WeatherNotificationHelper.postWeatherAlertNotification(
          context = context,
          tripId = trip.id,
          destination = dest,
          title = "Weather Packing Alert",
          message = msg,
        )
      }
    }
  }

  val allItems = (itemsState as? UiState.Success)?.data.orEmpty()
  val packedCount = allItems.count { it.isPacked }
  val totalCount = allItems.size
  val progress = if (totalCount == 0) 0f else packedCount.toFloat() / totalCount.toFloat()

  val filteredItems = remember(allItems, selectedFilter) {
    when (selectedFilter) {
      ChecklistFilter.ALL -> allItems
      ChecklistFilter.UNPACKED -> allItems.filter { !it.isPacked }
      ChecklistFilter.ALWAYS_BRING -> allItems.filter { it.isAlwaysBring }
      ChecklistFilter.TRIP_CONTEXT -> allItems.filter { !it.isAlwaysBring }
    }
  }

  val groupedItems = remember(filteredItems) {
    filteredItems.groupBy { it.category }.toSortedMap()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = trip.destination,
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = "${trip.durationDays} days · $packedCount/$totalCount packed",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("trip_detail_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = onOpenGoogleTasksImport,
            modifier = Modifier.testTag("detail_import_tasks_button"),
          ) {
            Icon(Icons.Rounded.Download, contentDescription = "Import from Google Tasks")
          }
          IconButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.testTag("detail_reset_trip_button"),
          ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = "Complete & Reset Trip")
          }
          Box {
            IconButton(
              onClick = { showMenu = true },
              modifier = Modifier.testTag("detail_more_menu_button"),
            ) {
              Icon(Icons.Rounded.MoreVert, contentDescription = "More trip options")
            }
            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text("Refresh Weather & Send Alert") },
                leadingIcon = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
                onClick = {
                  showMenu = false
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !WeatherNotificationHelper.hasNotificationPermission(context)
                  ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    viewModel.refreshLiveWeatherForTrip(trip) { dest, msg ->
                      WeatherNotificationHelper.postWeatherAlertNotification(
                        context = context,
                        tripId = trip.id,
                        destination = dest,
                        title = "Weather Packing Alert",
                        message = msg,
                      )
                    }
                  }
                },
              )
              DropdownMenuItem(
                text = { Text("Delete Trip") },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                onClick = {
                  showMenu = false
                  showDeleteDialog = true
                },
              )
            }
          }
        },
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddItemDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("detail_add_item_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add packing item")
      }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Progress & Weather Status Card
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
                Text(
                  text = "Packing Progress",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "${(progress * 100).toInt()}%",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Spacer(modifier = Modifier.height(8.dp))
              LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
              )

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .background(
                    if (trip.weatherAlertActive) {
                      MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    } else {
                      MaterialTheme.colorScheme.surfaceContainer
                    }
                  )
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = if (trip.weatherAlertActive) {
                    Icons.Rounded.WarningAmber
                  } else {
                    Icons.Rounded.NotificationsActive
                  },
                  contentDescription = null,
                  tint = if (trip.weatherAlertActive) {
                    MaterialTheme.colorScheme.secondary
                  } else {
                    MaterialTheme.colorScheme.primary
                  },
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = trip.weatherSummary.ifBlank { "Weather forecast" },
                    style = MaterialTheme.typography.labelLarge,
                  )
                  if (trip.weatherAlertActive && trip.weatherAlertMessage.isNotBlank()) {
                    Text(
                      text = trip.weatherAlertMessage,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                TextButton(
                  onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                      !WeatherNotificationHelper.hasNotificationPermission(context)
                    ) {
                      notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                      viewModel.refreshLiveWeatherForTrip(trip) { dest, msg ->
                        WeatherNotificationHelper.postWeatherAlertNotification(
                          context = context,
                          tripId = trip.id,
                          destination = dest,
                          title = "Weather Packing Alert",
                          message = msg,
                        )
                      }
                    }
                  },
                  modifier = Modifier.testTag("refresh_weather_alert_button"),
                ) {
                  Text("Check")
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = { showResetDialog = true },
                  modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("complete_reset_trip_cta"),
                ) {
                  Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Finish & Reset", maxLines = 1)
                }
                OutlinedButton(
                  onClick = onOpenGoogleTasksImport,
                  modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("import_google_tasks_cta"),
                ) {
                  Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Google Tasks", maxLines = 1)
                }
              }
            }
          }
        }

        // Filter Chips
        item {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            ChecklistFilter.entries.forEach { filter ->
              FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
              )
            }
          }
        }

        // Checklist Items
        when (itemsState) {
          is UiState.Loading -> {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }
          }
          is UiState.Error -> {
            item {
              Text(
                text = itemsState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
          is UiState.Success -> {
            if (groupedItems.isEmpty()) {
              item {
                Card(
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = if (selectedFilter == ChecklistFilter.UNPACKED && totalCount > 0) {
                      "Everything is packed! Ready to travel — tap \"Finish & Reset\" after your trip to keep your list fresh."
                    } else {
                      "No items match this filter yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                  )
                }
              }
            } else {
              groupedItems.forEach { (category, items) ->
                item(key = "header_$category") {
                  Text(
                    text = "$category (${items.count { it.isPacked }}/${items.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                  )
                }
                items(items, key = { it.id }) { item ->
                  PackingItemRow(
                    item = item,
                    onTogglePacked = { viewModel.toggleItemPacked(trip.id, item) },
                    onPromoteAlwaysBring = { viewModel.promoteToAlwaysBring(trip.id, item) },
                    onDelete = { viewModel.deleteTripItem(trip.id, item.id) },
                  )
                }
              }
            }
          }
          is UiState.Idle -> Unit
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
      }
    }
  }

  if (showAddItemDialog) {
    AddPackingItemDialog(
      onDismiss = { showAddItemDialog = false },
      onAdd = { name, category, qty, saveAlwaysBring ->
        viewModel.addManualItemToTrip(trip.id, name, category, qty, saveAlwaysBring)
        showAddItemDialog = false
      },
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("Reset After Trip?") },
      text = {
        Text(
          "Resetting unchecks every item on this checklist (including Always Bring essentials) so your list is completely fresh for your next journey."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.completeAndResetTrip(trip, allItems, reopenImmediately = false)
            showResetDialog = false
          },
          modifier = Modifier.testTag("confirm_complete_reset_button"),
        ) {
          Text("Archive & Reset")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              viewModel.completeAndResetTrip(trip, allItems, reopenImmediately = true)
              showResetDialog = false
            },
            modifier = Modifier.testTag("confirm_reuse_fresh_button"),
          ) {
            Text("Uncheck & Keep Active")
          }
          TextButton(onClick = { showResetDialog = false }) {
            Text("Cancel")
          }
        }
      },
    )
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text("Delete Trip?") },
      text = { Text("This removes ${trip.destination} and its trip-specific checklist items. Your Always Bring vault remains safe.") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            showDeleteDialog = false
            viewModel.deleteTrip(trip.id, onNavigateBack)
          },
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun PackingItemRow(
  item: PackingItem,
  onTogglePacked: () -> Unit,
  onPromoteAlwaysBring: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(
      containerColor = if (item.isPacked) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onTogglePacked)
      .testTag("packing_item_${item.id}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Checkbox(
        checked = item.isPacked,
        onCheckedChange = { onTogglePacked() },
        modifier = Modifier.testTag("packing_checkbox_${item.id}"),
      )
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (item.quantity > 1L) "${item.name} ×${item.quantity}" else item.name,
            style = MaterialTheme.typography.bodyLarge.copy(
              textDecoration = if (item.isPacked) TextDecoration.LineThrough else TextDecoration.None,
              fontWeight = FontWeight.Medium,
            ),
            color = if (item.isPacked) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.onSurface
            },
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (item.isAlwaysBring) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
            },
          ) {
            Text(
              text = if (item.isAlwaysBring) "ALWAYS BRING" else item.sourceType.uppercase().replace("_", " "),
              style = MaterialTheme.typography.labelSmall,
              color = if (item.isAlwaysBring) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSecondaryContainer
              },
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
          if (item.contextReason.isNotBlank() && !item.isAlwaysBring) {
            Text(
              text = item.contextReason,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
      if (!item.isAlwaysBring) {
        IconButton(
          onClick = onPromoteAlwaysBring,
          modifier = Modifier.size(44.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.StarOutline,
            contentDescription = "Save to Always Bring",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      } else {
        Icon(
          imageVector = Icons.Rounded.Star,
          contentDescription = "Always Bring item",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .padding(horizontal = 10.dp)
            .size(20.dp),
        )
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(44.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Remove item",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AddPackingItemDialog(
  onDismiss: () -> Unit,
  onAdd: (String, String, Long, Boolean) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Essentials") }
  var quantityText by remember { mutableStateOf("1") }
  var saveAlwaysBring by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Packing Item") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Item name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_item_name_input"),
        )
        OutlinedTextField(
          value = category,
          onValueChange = { category = it },
          label = { Text("Category") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = quantityText,
          onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
          label = { Text("Quantity") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { saveAlwaysBring = !saveAlwaysBring },
        ) {
          Checkbox(
            checked = saveAlwaysBring,
            onCheckedChange = { saveAlwaysBring = it },
          )
          Text(
            text = "Also save to Always Bring vault",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val qty = quantityText.toLongOrNull()?.coerceIn(1L, 99L) ?: 1L
          onAdd(name, category, qty, saveAlwaysBring)
        },
        enabled = name.isNotBlank(),
        modifier = Modifier.testTag("confirm_add_item_button"),
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
