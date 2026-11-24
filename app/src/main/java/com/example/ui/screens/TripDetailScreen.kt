package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsTransit
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryPreferences
import com.example.data.local.HomeBasePreferences
import com.example.data.local.HomeCountryPreference
import com.example.data.local.HomeCountrySource
import com.example.data.model.AiItemRecommendation
import com.example.data.model.AttractionSuggestion
import com.example.data.model.CATEGORY_ALWAYS_DO
import com.example.data.model.DEFAULT_TRIP_ACTIVITIES
import com.example.data.model.DraftPackingItem
import com.example.data.model.PackingItem
import com.example.data.model.TravelGuideInfo
import com.example.data.model.Trip
import com.example.data.model.TripItemScope
import com.example.data.model.UiState
import com.example.data.remote.WeatherRepository
import com.example.ui.components.AiRecommendationsSheet
import com.example.ui.components.CategorySectionHeader
import com.example.ui.components.DestinationHeaderImage
import com.example.ui.components.EditCategoryDialog
import com.example.ui.components.ManageCategoriesDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ChecklistFilter(val label: String) {
  ALL("All"),
  UNPACKED("Remaining"),
  ALWAYS_DO("Always-Do"),
  ALWAYS_BRING("Always Bring"),
  TRIP_CONTEXT("Trip-Specific"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(
  trip: Trip,
  itemsState: UiState<List<PackingItem>>,
  aiEnrichmentState: UiState<String> = UiState.Idle,
  aiRecommendationsState: UiState<AiItemRecommendation> = UiState.Idle,
  guideLoadingState: UiState<Unit> = UiState.Idle,
  homeCountry: HomeCountryPreference = HomeCountryPreference("US", "United States", HomeCountrySource.DEVICE_REGION),
  customCategories: List<String> = emptyList(),
  onNavigateBack: () -> Unit,
  onTogglePacked: (PackingItem) -> Unit,
  onAddManualItem: (name: String, category: String, reason: String, quantity: Long, scope: TripItemScope) -> Unit,
  onDeleteItem: (String) -> Unit,
  onRecategorizeItem: (
    item: PackingItem,
    newScope: TripItemScope,
    newCategory: String,
    saveToVault: Boolean,
    newName: String,
    newQuantity: Long,
    newReason: String,
  ) -> Unit,
  onAddCustomCategory: (String) -> Unit = {},
  onRenameCategory: (oldCategory: String, newCategory: String, removing: Boolean) -> Unit = { _, _, _ -> },
  onDeleteCustomCategory: (String) -> Unit = {},
  onRefreshWeather: () -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onOpenGoogleTasksImport: () -> Unit,
  onOpenAiRecommendations: (forceRefresh: Boolean) -> Unit = {},
  onAddRecommendedItems: (List<DraftPackingItem>) -> Unit = {},
  onDismissRecommendedItem: (String) -> Unit = {},
  onClearAiRecommendationsError: () -> Unit = {},
  onRetryAiEnrichment: () -> Unit = {},
  onDismissAiEnrichmentError: () -> Unit = {},
  onGenerateTravelGuide: () -> Unit = {},
  onDismissGuideError: () -> Unit = {},
  onAddSuggestedGuideItem: (name: String, category: String, reason: String) -> Unit = { _, _, _ -> },
  onDismissAttraction: (AttractionSuggestion) -> Unit = {},
  onUpdateTripActivities: (updatedActivities: List<String>, newlyAdded: List<String>, fetchSuggestions: Boolean) -> Unit = { _, _, _ -> },
  onCompleteAndResetTrip: (List<PackingItem>, Boolean) -> Unit,
  onDeleteTrip: () -> Unit,
) {
  var activeFilter by remember { mutableStateOf(ChecklistFilter.ALL) }
  var showAddDialog by remember { mutableStateOf(false) }
  var showAddActivityDialog by remember { mutableStateOf(false) }
  var showAiRecommendationsSheet by remember { mutableStateOf(false) }
  var showManageCategoriesDialog by remember { mutableStateOf(false) }
  var editingCategoryName by remember { mutableStateOf<String?>(null) }
  var collapsedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
  var recategorizingItem by remember { mutableStateOf<PackingItem?>(null) }
  var showResetDialog by remember { mutableStateOf(false) }
  var showDeleteTripDialog by remember { mutableStateOf(false) }

  val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
  val startLabel = LocalDate.ofEpochDay(trip.startDateEpochDay).format(dateFmt)
  val endLabel = LocalDate.ofEpochDay(trip.endDateEpochDay).format(dateFmt)

  val allItems = (itemsState as? UiState.Success)?.data.orEmpty()
  val packedCount = remember(allItems) { allItems.count { it.isPacked } }
  val alwaysDoCount = remember(allItems) { allItems.count { it.isAlwaysDo } }
  val alwaysBringCount = remember(allItems) { allItems.count { it.isAlwaysBring && !it.isAlwaysDo } }
  val tripSpecificCount = remember(allItems) { allItems.count { !it.isAlwaysBring && !it.isAlwaysDo } }
  val availableCategories = remember(allItems, customCategories) {
    CategoryPreferences.mergeAvailableCategories(
      activeCategories = allItems.filter { !it.isAlwaysDo }.map { it.category },
      customCategories = customCategories,
    )
  }
  val filteredItems = remember(allItems, activeFilter) {
    when (activeFilter) {
      ChecklistFilter.ALL -> allItems
      ChecklistFilter.UNPACKED -> allItems.filter { !it.isPacked }
      ChecklistFilter.ALWAYS_DO -> allItems.filter { it.isAlwaysDo }
      ChecklistFilter.ALWAYS_BRING -> allItems.filter { it.isAlwaysBring && !it.isAlwaysDo }
      ChecklistFilter.TRIP_CONTEXT -> allItems.filter { !it.isAlwaysBring && !it.isAlwaysDo }
    }
  }
  val groupedSections = remember(filteredItems) {
    filteredItems
      .groupBy { CategoryPreferences.displayCategoryFor(it) }
      .entries
      .sortedWith(
        compareBy<Map.Entry<String, List<PackingItem>>> {
          CategoryPreferences.categorySortOrder(it.key)
        }.thenBy { it.key.lowercase(Locale.ROOT) }
      )
      .map { it.key to it.value }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = trip.destination,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "$startLabel – $endLabel · ${trip.durationDays} day(s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("detail_back_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = {
              showAiRecommendationsSheet = true
              onOpenAiRecommendations(false)
            },
            modifier = Modifier.testTag("detail_top_ai_enrich_button"),
          ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI Packing Recommendations")
          }
          IconButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.testTag("detail_top_reset_button"),
          ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = "Complete & Reset Trip")
          }
          IconButton(
            onClick = { showDeleteTripDialog = true },
            modifier = Modifier.testTag("detail_delete_trip_button"),
          ) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Delete Trip",
              tint = MaterialTheme.colorScheme.error,
            )
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
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
                  .height(145.dp),
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
                        colors = listOf(Color(0x28062529), Color(0xD9062529)),
                      )
                    ),
                )
                Column(
                  modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                ) {
                  Text(
                    text = trip.formattedDestinationLabel(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "$packedCount of ${allItems.size} items packed",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFCDE9EC),
                  )
                }
              }

              Column(modifier = Modifier.padding(14.dp)) {
                val progress = if (allItems.isEmpty()) 0f else packedCount.toFloat() / allItems.size.toFloat()
                LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                      imageVector = if (trip.weatherAlertActive) Icons.Rounded.WarningAmber else Icons.Rounded.WbSunny,
                      contentDescription = null,
                      tint = if (trip.weatherAlertActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = WeatherRepository.formatSummaryForUnit(trip, homeCountry.temperatureUnit)
                        .ifBlank { "Tap Refresh for live weather" },
                      style = MaterialTheme.typography.bodySmall,
                    )
                  }
                  TextButton(
                    onClick = onRefreshWeather,
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("detail_refresh_weather_button"),
                  ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", maxLines = 1, softWrap = false)
                  }
                }

                val displayAlert = WeatherRepository.formatAlertForUnit(trip, homeCountry.temperatureUnit)
                if (trip.weatherAlertActive && displayAlert.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = displayAlert,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.padding(10.dp),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Weather packing alert notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Switch(
                    checked = trip.weatherNotificationsEnabled,
                    onCheckedChange = onToggleNotifications,
                    modifier = Modifier.testTag("detail_notifications_switch"),
                  )
                }
              }
            }
          }
        }

        if (aiEnrichmentState is UiState.Loading) {
          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Enriching checklist with Gemini AI...",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        } else if (aiEnrichmentState is UiState.Error) {
          item {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("detail_ai_error_banner"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
              ) {
                Text(
                  text = "AI enrichment unavailable — your Always Bring, weather, and manual items are loaded below.",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = aiEnrichmentState.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                ) {
                  TextButton(
                    onClick = onDismissAiEnrichmentError,
                    modifier = Modifier.testTag("detail_ai_error_dismiss_button"),
                  ) {
                    Text("Dismiss", maxLines = 1, softWrap = false)
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Button(
                    onClick = onRetryAiEnrichment,
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("detail_ai_error_retry_button"),
                  ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry AI", maxLines = 1, softWrap = false)
                  }
                }
              }
            }
          }
        }

        item {
          PlannedActivitiesCard(
            activities = trip.activities,
            onAddActivityClick = { showAddActivityDialog = true },
            onRemoveActivity = { activityToRemove ->
              val updated = trip.activities.filterNot { it.equals(activityToRemove, ignoreCase = true) }
              onUpdateTripActivities(updated, emptyList(), false)
            },
            onSuggestForActivities = {
              showAiRecommendationsSheet = true
              onOpenAiRecommendations(true)
            },
          )
        }

        item {
          val existingItemNames = remember(allItems) {
            allItems.map { it.name.trim().lowercase(Locale.ROOT) }.toSet()
          }
          val isKnownDomestic = remember(homeCountry, trip.country) {
            HomeBasePreferences.isTripInternational(homeCountry, trip.country) == false
          }
          DestinationGuideCard(
            destination = trip.destination,
            homeCountryName = homeCountry.countryName,
            isKnownDomestic = isKnownDomestic,
            guide = trip.travelGuide,
            loadingState = guideLoadingState,
            existingItemNames = existingItemNames,
            onGenerateOrRefresh = onGenerateTravelGuide,
            onDismissError = onDismissGuideError,
            onAddSuggestedItem = onAddSuggestedGuideItem,
            onDismissAttraction = onDismissAttraction,
          )
        }

        item {
          BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wideActions = maxWidth >= 480.dp
            if (wideActions) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = { showAddDialog = true },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_add_item_button"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Add Item", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                  onClick = onOpenGoogleTasksImport,
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_import_tasks_button"),
                ) {
                  Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Import Tasks", maxLines = 1, softWrap = false)
                }
                OutlinedButton(
                  onClick = { showResetDialog = true },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("detail_reset_trip_button"),
                ) {
                  Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Reset Checklist", maxLines = 1, softWrap = false)
                }
              }
            } else {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Button(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("detail_add_item_button"),
                  ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Item", maxLines = 1, softWrap = false)
                  }
                  OutlinedButton(
                    onClick = onOpenGoogleTasksImport,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("detail_import_tasks_button"),
                  ) {
                    Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Tasks", maxLines = 1, softWrap = false)
                  }
                }
                OutlinedButton(
                  onClick = { showResetDialog = true },
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("detail_reset_trip_button"),
                ) {
                  Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Reset Checklist", maxLines = 1, softWrap = false)
                }
              }
            }
          }
        }

        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              ChecklistFilter.entries.forEach { filter ->
                val chipText = when (filter) {
                  ChecklistFilter.ALWAYS_DO -> if (alwaysDoCount > 0) "${filter.label} ($alwaysDoCount)" else filter.label
                  ChecklistFilter.ALWAYS_BRING -> if (alwaysBringCount > 0) "${filter.label} ($alwaysBringCount)" else filter.label
                  ChecklistFilter.TRIP_CONTEXT -> if (tripSpecificCount > 0) "${filter.label} ($tripSpecificCount)" else filter.label
                  else -> filter.label
                }
                FilterChip(
                  selected = activeFilter == filter,
                  onClick = { activeFilter = filter },
                  label = { Text(chipText) },
                  modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
                )
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Grouped by Category (${groupedSections.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              TextButton(
                onClick = { showManageCategoriesDialog = true },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("detail_manage_categories_button"),
              ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Categories", maxLines = 1, softWrap = false)
              }
            }
          }
        }

        when (itemsState) {
          is UiState.Loading, is UiState.Idle -> {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
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
                modifier = Modifier.padding(12.dp),
              )
            }
          }
          is UiState.Success -> {
            if (groupedSections.isEmpty()) {
              item {
                Card(
                  shape = MaterialTheme.shapes.medium,
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = if (allItems.isEmpty()) {
                      "No items in this checklist yet. Tap Add Item or Import Tasks."
                    } else {
                      "No items match the \"${activeFilter.label}\" filter."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                  )
                }
              }
            } else {
              groupedSections.forEach { (categoryName, sectionItems) ->
                val catKey = categoryName.lowercase(Locale.ROOT)
                val isExpanded = catKey !in collapsedCategories
                val isAlwaysDoCategory = categoryName.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)
                item(key = "section_header_$catKey") {
                  CategorySectionHeader(
                    categoryName = categoryName,
                    packedCount = sectionItems.count { it.isPacked },
                    totalCount = sectionItems.size,
                    isExpanded = isExpanded,
                    canEditCategory = !isAlwaysDoCategory,
                    onToggleExpand = {
                      collapsedCategories = if (isExpanded) {
                        collapsedCategories + catKey
                      } else {
                        collapsedCategories - catKey
                      }
                    },
                    onEditCategory = { editingCategoryName = categoryName },
                  )
                }
                if (isExpanded) {
                  items(sectionItems, key = { it.id }) { item ->
                    PackingItemRowCard(
                      item = item,
                      onTogglePacked = { onTogglePacked(item) },
                      onRecategorize = { recategorizingItem = item },
                      onDelete = { onDeleteItem(item.id) },
                    )
                  }
                }
              }
            }
          }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
      }
    }
  }

  if (showAddDialog) {
    var name by remember { mutableStateOf("") }
    var selectedScope by remember {
      mutableStateOf(
        if (activeFilter == ChecklistFilter.ALWAYS_DO) TripItemScope.ALWAYS_DO else TripItemScope.TRIP_SPECIFIC
      )
    }
    var category by remember { mutableStateOf("Essentials") }
    var qtyText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Item or Task") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = {
              Text(
                if (selectedScope == TripItemScope.ALWAYS_DO) {
                  "Task name (e.g. Empty the trash)"
                } else {
                  "Item name"
                }
              )
            },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("detail_add_name_input"),
          )
          Text(
            text = "Type",
            style = MaterialTheme.typography.labelLarge,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            FilterChip(
              selected = selectedScope == TripItemScope.TRIP_SPECIFIC,
              onClick = { selectedScope = TripItemScope.TRIP_SPECIFIC },
              label = { Text("This Trip") },
            )
            FilterChip(
              selected = selectedScope == TripItemScope.ALWAYS_BRING,
              onClick = { selectedScope = TripItemScope.ALWAYS_BRING },
              label = { Text("Always Bring") },
            )
            FilterChip(
              selected = selectedScope == TripItemScope.ALWAYS_DO,
              onClick = { selectedScope = TripItemScope.ALWAYS_DO },
              label = { Text("Always-Do") },
            )
          }
          if (selectedScope != TripItemScope.ALWAYS_DO) {
            Text(
              text = "Category",
              style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              availableCategories.forEach { preset ->
                FilterChip(
                  selected = category.equals(preset, ignoreCase = true),
                  onClick = { category = preset },
                  label = { Text(preset) },
                )
              }
            }
            OutlinedTextField(
              value = category,
              onValueChange = { category = it },
              label = { Text("Custom category") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = qtyText,
              onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
              label = { Text("Quantity") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            Text(
              text = "Saved under Always-Do (home tasks before leaving) and added to future trips automatically.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Notes / Reason") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onAddManualItem(
              name,
              if (selectedScope == TripItemScope.ALWAYS_DO) CATEGORY_ALWAYS_DO else category.ifBlank { "Essentials" },
              reason,
              if (selectedScope == TripItemScope.ALWAYS_DO) 1L else qtyText.toLongOrNull()?.coerceIn(1L, 99L) ?: 1L,
              selectedScope,
            )
            showAddDialog = false
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("detail_confirm_add_button"),
        ) {
          Text("Add")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  recategorizingItem?.let { item ->
    var itemName by remember(item.id) { mutableStateOf(item.name) }
    var selectedScope by remember(item.id) { mutableStateOf(item.scope) }
    var category by remember(item.id) {
      mutableStateOf(
        if (item.category.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)) {
          "Essentials"
        } else {
          item.category.ifBlank { "Essentials" }
        }
      )
    }
    var qtyText by remember(item.id) { mutableStateOf(item.quantity.coerceIn(1L, 99L).toString()) }
    var reasonText by remember(item.id) { mutableStateOf(item.contextReason) }
    var saveToVault by remember(item.id) { mutableStateOf(true) }

    AlertDialog(
      onDismissRequest = { recategorizingItem = null },
      title = {
        Text(
          if (selectedScope == TripItemScope.ALWAYS_DO) "Edit Task" else "Edit Item"
        )
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = {
              Text(if (selectedScope == TripItemScope.ALWAYS_DO) "Task name" else "Item name")
            },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_item_name_input"),
          )

          if (selectedScope != TripItemScope.ALWAYS_DO) {
            OutlinedTextField(
              value = qtyText,
              onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
              label = { Text("Quantity") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_item_qty_input"),
            )
          }

          Text(
            text = "List Group",
            style = MaterialTheme.typography.labelLarge,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            FilterChip(
              selected = selectedScope == TripItemScope.TRIP_SPECIFIC,
              onClick = { selectedScope = TripItemScope.TRIP_SPECIFIC },
              label = { Text("Trip-Specific") },
            )
            FilterChip(
              selected = selectedScope == TripItemScope.ALWAYS_BRING,
              onClick = { selectedScope = TripItemScope.ALWAYS_BRING },
              label = { Text("Always Bring") },
            )
            FilterChip(
              selected = selectedScope == TripItemScope.ALWAYS_DO,
              onClick = { selectedScope = TripItemScope.ALWAYS_DO },
              label = { Text("Always-Do") },
            )
          }

          if (selectedScope == TripItemScope.ALWAYS_DO) {
            Text(
              text = "Grouped under the Always-Do category for pre-departure home tasks (before you leave).",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              text = "Packing Category",
              style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              availableCategories.forEach { preset ->
                FilterChip(
                  selected = category.equals(preset, ignoreCase = true),
                  onClick = { category = preset },
                  label = { Text(preset) },
                )
              }
            }
            OutlinedTextField(
              value = category,
              onValueChange = { category = it },
              label = { Text("Custom category") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }

          OutlinedTextField(
            value = reasonText,
            onValueChange = { reasonText = it },
            label = { Text("Notes / Reason") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_item_reason_input"),
          )

          if (selectedScope == TripItemScope.ALWAYS_BRING || selectedScope == TripItemScope.ALWAYS_DO) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Save to Always vault for future trips",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              Switch(
                checked = saveToVault,
                onCheckedChange = { saveToVault = it },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onRecategorizeItem(
              item,
              selectedScope,
              if (selectedScope == TripItemScope.ALWAYS_DO) CATEGORY_ALWAYS_DO else category.ifBlank { "Essentials" },
              saveToVault && selectedScope != TripItemScope.TRIP_SPECIFIC,
              itemName.trim(),
              qtyText.toLongOrNull()?.coerceIn(1L, 99L) ?: 1L,
              reasonText,
            )
            recategorizingItem = null
          },
          enabled = itemName.isNotBlank(),
          modifier = Modifier.testTag("confirm_recategorize_item_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { recategorizingItem = null }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("Complete & Reset Checklist") },
      text = {
        Text(
          "Resetting unchecks all items so your ${trip.destination} checklist is ready for your next journey while keeping your Always Bring and Always-Do items intact."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onCompleteAndResetTrip(allItems, true)
            showResetDialog = false
          },
          modifier = Modifier.testTag("confirm_complete_and_reset_button"),
        ) {
          Text("Complete & Reset")
        }
      },
      dismissButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { showResetDialog = false }) {
            Text("Cancel")
          }
          OutlinedButton(
            onClick = {
              onCompleteAndResetTrip(allItems, false)
              showResetDialog = false
            },
          ) {
            Text("Reset Only")
          }
        }
      },
    )
  }

  if (showDeleteTripDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteTripDialog = false },
      title = { Text("Delete ${trip.destination}?") },
      text = {
        Text("This permanently deletes this trip and all checklist items for it. Your Always Bring vault remains unchanged.")
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteTripDialog = false
            onDeleteTrip()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("confirm_delete_trip_button"),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteTripDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showAiRecommendationsSheet) {
    AiRecommendationsSheet(
      destination = trip.destination,
      recommendationsState = aiRecommendationsState,
      onRefresh = { onOpenAiRecommendations(true) },
      onAddItems = onAddRecommendedItems,
      onDismissItem = onDismissRecommendedItem,
      onClearError = onClearAiRecommendationsError,
      onDismissSheet = { showAiRecommendationsSheet = false },
    )
  }

  if (showAddActivityDialog) {
    var customActivity by remember { mutableStateOf("") }
    var selectedPresets by remember(trip.id, showAddActivityDialog) {
      mutableStateOf<Set<String>>(emptySet())
    }
    var autoSuggest by remember(trip.id, showAddActivityDialog) { mutableStateOf(true) }
    val existingLower = remember(trip.activities) {
      trip.activities.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    }
    val remainingPresets = remember(existingLower) {
      DEFAULT_TRIP_ACTIVITIES.filter { it.lowercase(Locale.ROOT) !in existingLower }
    }

    AlertDialog(
      onDismissRequest = { showAddActivityDialog = false },
      title = { Text("Add Trip Activity") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Add activities to ${trip.destination} to get tailored packing suggestions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = customActivity,
            onValueChange = { customActivity = it },
            label = { Text("Custom activity") },
            placeholder = { Text("e.g., Scuba diving, Skiing, Concert") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("detail_activity_input"),
          )
          if (remainingPresets.isNotEmpty()) {
            Text(
              text = "Quick-Pick Activities",
              style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              remainingPresets.forEach { preset ->
                val isSelected = preset in selectedPresets
                FilterChip(
                  selected = isSelected,
                  onClick = {
                    selectedPresets = if (isSelected) {
                      selectedPresets - preset
                    } else {
                      selectedPresets + preset
                    }
                  },
                  label = { Text(preset) },
                )
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Get packing suggestions for added activities",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
            Switch(
              checked = autoSuggest,
              onCheckedChange = { autoSuggest = it },
              modifier = Modifier.testTag("detail_activity_autosuggest_switch"),
            )
          }
        }
      },
      confirmButton = {
        val hasNewInput = customActivity.isNotBlank() || selectedPresets.isNotEmpty()
        Button(
          onClick = {
            val newlyAdded = (selectedPresets.toList() + listOf(customActivity.trim()))
              .map { it.trim() }
              .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in existingLower }
              .distinctBy { it.lowercase(Locale.ROOT) }
            if (newlyAdded.isNotEmpty()) {
              val updated = (trip.activities + newlyAdded).take(15)
              onUpdateTripActivities(updated, newlyAdded, autoSuggest)
              showAddActivityDialog = false
              if (autoSuggest) {
                showAiRecommendationsSheet = true
              }
            } else {
              showAddActivityDialog = false
            }
          },
          enabled = hasNewInput,
          modifier = Modifier.testTag("confirm_add_activity_button"),
        ) {
          Text(if (autoSuggest) "Add & Suggest" else "Add Activity")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddActivityDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showManageCategoriesDialog) {
    val activeCounts = remember(allItems, availableCategories) {
      val countsMap = allItems
        .groupBy { CategoryPreferences.displayCategoryFor(it) }
        .mapValues { it.value.size }
      val allNames = buildList {
        if ((countsMap[CATEGORY_ALWAYS_DO] ?: 0) > 0) add(CATEGORY_ALWAYS_DO)
        availableCategories.forEach { add(it) }
      }
      allNames.distinctBy { it.lowercase(Locale.ROOT) }.map { name ->
        val matchedCount = countsMap.entries
          .firstOrNull { it.key.equals(name, ignoreCase = true) }
          ?.value ?: 0
        name to matchedCount
      }
    }
    ManageCategoriesDialog(
      categoryCounts = activeCounts,
      customCategories = customCategories,
      onAddCustomCategory = onAddCustomCategory,
      onEditCategory = { catName ->
        showManageCategoriesDialog = false
        editingCategoryName = catName
      },
      onDeleteCustomCategory = onDeleteCustomCategory,
      onDismiss = { showManageCategoriesDialog = false },
    )
  }

  editingCategoryName?.let { targetCat ->
    val itemCount = allItems.count {
      !it.isAlwaysDo && CategoryPreferences.displayCategoryFor(it).equals(targetCat, ignoreCase = true)
    }
    EditCategoryDialog(
      categoryName = targetCat,
      itemCount = itemCount,
      availableTargets = availableCategories,
      onRenameOrMove = { newCat ->
        onRenameCategory(targetCat, newCat, false)
      },
      onRemoveCategory = { fallback ->
        onRenameCategory(targetCat, fallback, true)
      },
      onDismiss = { editingCategoryName = null },
    )
  }
}

@Composable
private fun PackingItemRowCard(
  item: PackingItem,
  onTogglePacked: () -> Unit,
  onRecategorize: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(
      containerColor = if (item.isPacked) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
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
        modifier = Modifier.testTag("checkbox_item_${item.id}"),
      )
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (!item.isAlwaysDo && item.quantity > 1L) "${item.name} ×${item.quantity}" else item.name,
            style = MaterialTheme.typography.titleSmall.copy(
              fontWeight = FontWeight.SemiBold,
              textDecoration = if (item.isPacked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (item.isAlwaysDo) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Icon(
                  Icons.Rounded.Home,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "Always-Do",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
            }
          } else if (item.isAlwaysBring) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Icon(
                  Icons.Rounded.Shield,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "Always",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        }
        val subtitle = listOf(item.category, item.contextReason)
          .filter { it.isNotBlank() }
          .joinToString(" · ")
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      IconButton(
        onClick = onRecategorize,
        modifier = Modifier.testTag("recategorize_item_${item.id}"),
      ) {
        Icon(
          Icons.Rounded.Edit,
          contentDescription = "Edit ${item.name}",
          tint = MaterialTheme.colorScheme.primary,
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("delete_item_${item.id}"),
      ) {
        Icon(
          Icons.Rounded.DeleteOutline,
          contentDescription = "Delete ${item.name}",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationGuideCard(
  destination: String,
  homeCountryName: String,
  isKnownDomestic: Boolean,
  guide: TravelGuideInfo?,
  loadingState: UiState<Unit>,
  existingItemNames: Set<String>,
  onGenerateOrRefresh: () -> Unit,
  onDismissError: () -> Unit,
  onAddSuggestedItem: (name: String, category: String, reason: String) -> Unit,
  onDismissAttraction: (AttractionSuggestion) -> Unit,
) {
  var expanded by remember(destination) { mutableStateOf(true) }
  val showInternationalPrep = guide?.isInternationalTravel == true && !isKnownDomestic

  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)
      .testTag("destination_guide_card"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Destination Guide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
              if (showInternationalPrep) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  ) {
                    Icon(
                      Icons.Rounded.Public,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.size(11.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = "Int'l Prep",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                  }
                }
              } else if (isKnownDomestic) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  shape = MaterialTheme.shapes.small,
                  color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                  Text(
                    text = "Domestic",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
            }
            Text(
              text = if (isKnownDomestic) {
                "Attractions & local events · $homeCountryName"
              } else {
                "Attractions, events, adapter & eSIM"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (guide != null) {
          IconButton(
            onClick = onGenerateOrRefresh,
            enabled = loadingState !is UiState.Loading,
            modifier = Modifier.testTag("guide_refresh_button"),
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Destination Guide")
          }
          IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("guide_expand_toggle_button"),
          ) {
            Icon(
              imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription = if (expanded) "Collapse guide" else "Expand guide",
            )
          }
        }
      }

      if (loadingState is UiState.Loading) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (isKnownDomestic) {
              "Loading nearby attractions & local events..."
            } else {
              "Loading attractions, events, power adapter & eSIM tips..."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else if (loadingState is UiState.Error) {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = loadingState.message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              TextButton(onClick = onDismissError) { Text("Dismiss") }
              Spacer(modifier = Modifier.width(6.dp))
              Button(
                onClick = onGenerateOrRefresh,
                modifier = Modifier.heightIn(min = 48.dp),
              ) {
                Text("Retry")
              }
            }
          }
        }
      }

      if (guide == null && loadingState !is UiState.Loading) {
        Button(
          onClick = onGenerateOrRefresh,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("detail_generate_guide_button"),
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isKnownDomestic) "Get Attractions & Events" else "Get Attractions, Events & Int'l Prep",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else if (guide != null && expanded) {
        // 1. Power Adapter & eSIM Essentials (International trips only)
        if (showInternationalPrep && (guide.powerAdapterSummary.isNotBlank() || guide.esimSummary.isNotBlank())) {
          Text(
            text = "Power Adapter & Connectivity",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        if (showInternationalPrep && guide.powerAdapterSummary.isNotBlank()) {
          val adapterItemName = guide.powerAdapterChecklistItem.ifBlank { "Travel Power Adapter" }
          val alreadyAdded = adapterItemName.trim().lowercase(Locale.ROOT) in existingItemNames
          GuidePrepRow(
            icon = Icons.Rounded.Power,
            title = "Power Adapter",
            body = guide.powerAdapterSummary,
            actionLabel = if (alreadyAdded) "Added" else "Add Adapter",
            alreadyAdded = alreadyAdded,
            buttonTag = "guide_add_adapter_button",
            onAction = {
              onAddSuggestedItem(
                adapterItemName,
                "Tech",
                "Power & plug compatibility for $destination",
              )
            },
          )
        }

        if (showInternationalPrep && guide.esimSummary.isNotBlank()) {
          val esimItemName = guide.esimChecklistItem.ifBlank { "$destination Data eSIM" }
          val alreadyAdded = esimItemName.trim().lowercase(Locale.ROOT) in existingItemNames
          GuidePrepRow(
            icon = Icons.Rounded.SimCard,
            title = "eSIM & Mobile Data",
            body = guide.esimSummary,
            actionLabel = if (alreadyAdded) "Added" else "Add eSIM",
            alreadyAdded = alreadyAdded,
            buttonTag = "guide_add_esim_button",
            onAction = {
              onAddSuggestedItem(
                esimItemName,
                "Tech",
                "Connectivity plan for $destination",
              )
            },
          )
        }

        // 2. Currency, Transit & Entry Tips
        val tips = buildList {
          if (showInternationalPrep && guide.currencyAndPaymentTip.isNotBlank()) {
            add(Triple(Icons.Rounded.CurrencyExchange, "Currency & Payments", guide.currencyAndPaymentTip))
          }
          if (guide.transitTip.isNotBlank()) {
            add(Triple(Icons.Rounded.DirectionsTransit, "Local Transit", guide.transitTip))
          }
          if (guide.entryAndSafetyTip.isNotBlank() && (showInternationalPrep || !isKnownDomestic)) {
            add(Triple(Icons.Rounded.VerifiedUser, "Entry & Safety", guide.entryAndSafetyTip))
          }
        }
        if (tips.isNotEmpty()) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              tips.forEach { (icon, label, detail) ->
                Row(verticalAlignment = Alignment.Top) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                      .padding(top = 2.dp)
                      .size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(
                      text = label,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                      text = detail,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }
        }

        // 3. Nearby Attractions (Swipeable to dismiss)
        if (guide.nearbyAttractions.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Nearby Attractions",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Swipe to dismiss",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          guide.nearbyAttractions.forEachIndexed { index, attraction ->
            key("${attraction.name}_${attraction.area}") {
              val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                  if (value != SwipeToDismissBoxValue.Settled) {
                    onDismissAttraction(attraction)
                    true
                  } else {
                    false
                  }
                },
              )
              SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                  val alignEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .clip(MaterialTheme.shapes.medium)
                      .background(MaterialTheme.colorScheme.errorContainer)
                      .padding(horizontal = 16.dp),
                    contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Dismiss attraction",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                      )
                    }
                  }
                },
                modifier = Modifier.testTag("guide_attraction_swipe_$index"),
              ) {
                Surface(
                  shape = MaterialTheme.shapes.medium,
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                ) {
                  Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = attraction.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                      )
                      if (attraction.area.isNotBlank()) {
                        Surface(
                          shape = MaterialTheme.shapes.small,
                          color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                          Text(
                            text = attraction.area,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          )
                        }
                      }
                    }
                    if (attraction.description.isNotBlank()) {
                      Text(
                        text = attraction.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    if (attraction.suggestedItem.isNotBlank()) {
                      val added = attraction.suggestedItem.trim().lowercase(Locale.ROOT) in existingItemNames
                      OutlinedButton(
                        onClick = {
                          onAddSuggestedItem(
                            attraction.suggestedItem,
                            "Activity Gear",
                            "For visiting ${attraction.name}",
                          )
                        },
                        enabled = !added,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                          .heightIn(min = 48.dp)
                          .testTag("guide_attraction_add_$index"),
                      ) {
                        Icon(
                          imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                          contentDescription = null,
                          modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = if (added) "Added: ${attraction.suggestedItem}" else "Pack ${attraction.suggestedItem}",
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // 4. Local & Seasonal Events
        if (guide.localEvents.isNotEmpty()) {
          Text(
            text = "Events Around Your Dates",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          guide.localEvents.forEachIndexed { index, ev ->
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Rounded.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = ev.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                  )
                }
                val meta = listOf(ev.timing, ev.venue).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) {
                  Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                if (ev.description.isNotBlank()) {
                  Text(
                    text = ev.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                if (ev.suggestedItem.isNotBlank()) {
                  val added = ev.suggestedItem.trim().lowercase(Locale.ROOT) in existingItemNames
                  OutlinedButton(
                    onClick = {
                      onAddSuggestedItem(
                        ev.suggestedItem,
                        "Activity Gear",
                        "For event: ${ev.title}",
                      )
                    },
                    enabled = !added,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("guide_event_add_$index"),
                  ) {
                    Icon(
                      imageVector = if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = if (added) "Added: ${ev.suggestedItem}" else "Pack ${ev.suggestedItem}",
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GuidePrepRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  body: String,
  actionLabel: String,
  alreadyAdded: Boolean,
  buttonTag: String,
  onAction: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(verticalAlignment = Alignment.Top) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .padding(top = 2.dp)
            .size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      OutlinedButton(
        onClick = onAction,
        enabled = !alreadyAdded,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag(buttonTag),
      ) {
        Icon(
          imageVector = if (alreadyAdded) Icons.Rounded.Check else Icons.Rounded.Add,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(actionLabel, maxLines = 1, softWrap = false)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedActivitiesCard(
  activities: List<String>,
  onAddActivityClick: () -> Unit,
  onRemoveActivity: (String) -> Unit,
  onSuggestForActivities: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
      .testTag("planned_activities_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            imageVector = Icons.Rounded.Hiking,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Planned Activities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        TextButton(
          onClick = onAddActivityClick,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("detail_add_activity_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add Activity", maxLines = 1, softWrap = false)
        }
      }

      if (activities.isEmpty()) {
        Text(
          text = "No activities added yet. Add activities like Hiking, Beach & Swim, or Formal Event to receive extra packing suggestions.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          activities.forEach { activity ->
            InputChip(
              selected = true,
              onClick = { onRemoveActivity(activity) },
              label = { Text(activity) },
              trailingIcon = {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = "Remove $activity",
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("trip_activity_chip_$activity"),
            )
          }
        }
        OutlinedButton(
          onClick = onSuggestForActivities,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("detail_suggest_for_activities_button"),
        ) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Suggest Gear for Activities", maxLines = 1, softWrap = false)
        }
      }
    }
  }
}
