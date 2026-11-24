package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryPreferences
import com.example.data.model.AlwaysBringItem
import com.example.data.model.CATEGORY_ALWAYS_DO
import com.example.data.model.UiState
import com.example.data.model.VaultItemType
import com.example.ui.components.CategorySectionHeader
import com.example.ui.components.EditCategoryDialog
import com.example.ui.components.ManageCategoriesDialog
import java.util.Locale

private val ALWAYS_DO_SUGGESTIONS = listOf(
  "Empty the trash",
  "Lock doors & windows",
  "Adjust thermostat",
  "Water houseplants",
  "Unplug small appliances",
  "Take out recycling",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlwaysBringScreen(
  alwaysBringState: UiState<List<AlwaysBringItem>>,
  customCategories: List<String> = emptyList(),
  onAddAlwaysBring: (name: String, category: String, quantity: Long, notes: String) -> Unit,
  onAddAlwaysDoTask: (name: String, notes: String) -> Unit,
  onRecategorizeVaultItem: (
    item: AlwaysBringItem,
    targetType: VaultItemType,
    category: String,
    notes: String,
    newName: String,
    newQuantity: Long,
  ) -> Unit,
  onAddCustomCategory: (String) -> Unit = {},
  onRenameVaultCategory: (oldCategory: String, newCategory: String, removing: Boolean) -> Unit = { _, _, _ -> },
  onDeleteCustomCategory: (String) -> Unit = {},
  onDeleteAlwaysBring: (String) -> Unit,
  onImportGoogleTasks: (VaultItemType) -> Unit,
) {
  var activeTab by remember { mutableStateOf(VaultItemType.ALWAYS_BRING) }
  var showAddBringDialog by remember { mutableStateOf(false) }
  var showAddDoDialog by remember { mutableStateOf(false) }
  var showManageCategoriesDialog by remember { mutableStateOf(false) }
  var editingCategoryName by remember { mutableStateOf<String?>(null) }
  var collapsedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
  var editingItem by remember { mutableStateOf<AlwaysBringItem?>(null) }

  val allVaultItems = (alwaysBringState as? UiState.Success)?.data.orEmpty()
  val bringItems = remember(allVaultItems) { allVaultItems.filter { !it.isAlwaysDo } }
  val doItems = remember(allVaultItems) { allVaultItems.filter { it.isAlwaysDo } }
  val displayedItems = if (activeTab == VaultItemType.ALWAYS_BRING) bringItems else doItems
  val availableCategories = remember(bringItems, customCategories) {
    CategoryPreferences.mergeAvailableCategories(
      activeCategories = bringItems.map { it.category },
      customCategories = customCategories,
    )
  }
  val groupedBringSections = remember(bringItems) {
    bringItems
      .groupBy { it.category.trim().ifBlank { "Essentials" } }
      .entries
      .sortedWith(
        compareBy<Map.Entry<String, List<AlwaysBringItem>>> {
          CategoryPreferences.categorySortOrder(it.key)
        }.thenBy { it.key.lowercase(Locale.ROOT) }
      )
      .map { it.key to it.value }
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
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(
            containerColor = if (activeTab == VaultItemType.ALWAYS_BRING) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.tertiaryContainer
            },
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          val contentColor = if (activeTab == VaultItemType.ALWAYS_BRING) {
            MaterialTheme.colorScheme.onPrimaryContainer
          } else {
            MaterialTheme.colorScheme.onTertiaryContainer
          }

          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (activeTab == VaultItemType.ALWAYS_BRING) {
                  Icons.Rounded.Shield
                } else {
                  Icons.Rounded.Home
                },
                contentDescription = null,
                tint = contentColor,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (activeTab == VaultItemType.ALWAYS_BRING) {
                  "Always Bring Vault"
                } else {
                  "Always-Do Home Tasks"
                },
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = if (activeTab == VaultItemType.ALWAYS_BRING) {
                "Timeless packing essentials automatically included on every trip—kept separate from weather and activity gear."
              } else {
                "Things to do at home before you leave (like emptying the trash or locking up)—added cleanly under Always-Do on every trip."
              },
              style = MaterialTheme.typography.bodySmall,
              color = contentColor,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = {
                  if (activeTab == VaultItemType.ALWAYS_BRING) {
                    showAddBringDialog = true
                  } else {
                    showAddDoDialog = true
                  }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag(
                    if (activeTab == VaultItemType.ALWAYS_BRING) {
                      "add_always_bring_button"
                    } else {
                      "add_always_do_button"
                    }
                  ),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (activeTab == VaultItemType.ALWAYS_BRING) "Add Item" else "Add Task",
                  maxLines = 1,
                  softWrap = false,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              OutlinedButton(
                onClick = { onImportGoogleTasks(activeTab) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("always_bring_import_tasks_button"),
              ) {
                Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Tasks", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }

      item {
        PrimaryTabRow(
          selectedTabIndex = if (activeTab == VaultItemType.ALWAYS_BRING) 0 else 1,
        ) {
          Tab(
            selected = activeTab == VaultItemType.ALWAYS_BRING,
            onClick = { activeTab = VaultItemType.ALWAYS_BRING },
            text = {
              Text(
                text = "Always Bring (${bringItems.size})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier.testTag("vault_tab_always_bring"),
          )
          Tab(
            selected = activeTab == VaultItemType.ALWAYS_DO,
            onClick = { activeTab = VaultItemType.ALWAYS_DO },
            text = {
              Text(
                text = "Always-Do (${doItems.size})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            modifier = Modifier.testTag("vault_tab_always_do"),
          )
        }
      }

      when (alwaysBringState) {
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
              text = alwaysBringState.message,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
        is UiState.Success -> {
          if (displayedItems.isEmpty()) {
            item {
              Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                  .fillMaxWidth()
                  .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  if (activeTab == VaultItemType.ALWAYS_DO) {
                    Text(
                      text = "No Always-Do home tasks yet",
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                      text = "Tap any common pre-departure chore below to add it, or move an existing item from Always Bring.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      ALWAYS_DO_SUGGESTIONS.forEach { suggestion ->
                        SuggestionChip(
                          onClick = { onAddAlwaysDoTask(suggestion, "Before leaving home on every trip") },
                          label = { Text("+ $suggestion") },
                        )
                      }
                    }
                  } else {
                    Text(
                      text = "No Always Bring items yet. Tap Add Item to start your essentials vault.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          } else if (activeTab == VaultItemType.ALWAYS_BRING) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Categories (${groupedBringSections.size})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                  onClick = { showManageCategoriesDialog = true },
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("vault_manage_categories_button"),
                ) {
                  Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Edit Categories", maxLines = 1, softWrap = false)
                }
              }
            }
            groupedBringSections.forEach { (categoryName, sectionItems) ->
              val catKey = categoryName.lowercase(Locale.ROOT)
              val isExpanded = catKey !in collapsedCategories
              item(key = "vault_section_$catKey") {
                CategorySectionHeader(
                  categoryName = categoryName,
                  packedCount = null,
                  totalCount = sectionItems.size,
                  isExpanded = isExpanded,
                  canEditCategory = true,
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
                  VaultItemCard(
                    item = item,
                    onEdit = { editingItem = item },
                    onDelete = { onDeleteAlwaysBring(item.id) },
                  )
                }
              }
            }
          } else {
            val doKey = CATEGORY_ALWAYS_DO.lowercase(Locale.ROOT)
            val isDoExpanded = doKey !in collapsedCategories
            item(key = "vault_section_$doKey") {
              CategorySectionHeader(
                categoryName = CATEGORY_ALWAYS_DO,
                packedCount = null,
                totalCount = displayedItems.size,
                isExpanded = isDoExpanded,
                canEditCategory = false,
                onToggleExpand = {
                  collapsedCategories = if (isDoExpanded) {
                    collapsedCategories + doKey
                  } else {
                    collapsedCategories - doKey
                  }
                },
                onEditCategory = {},
              )
            }
            if (isDoExpanded) {
              items(displayedItems, key = { it.id }) { item ->
                VaultItemCard(
                  item = item,
                  onEdit = { editingItem = item },
                  onDelete = { onDeleteAlwaysBring(item.id) },
                )
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }

  if (showAddBringDialog) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Essentials") }
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("Always bring on every trip") }

    AlertDialog(
      onDismissRequest = { showAddBringDialog = false },
      title = { Text("Add Always Bring Essential") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("always_bring_name_input"),
          )
          Text(
            text = "Category",
            style = MaterialTheme.typography.labelLarge,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            availableCategories.forEach { cat ->
              FilterChip(
                selected = category.equals(cat, ignoreCase = true),
                onClick = { category = cat },
                label = { Text(cat) },
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
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onAddAlwaysBring(
              name,
              category.ifBlank { "Essentials" },
              qtyText.toLongOrNull()?.coerceIn(1L, 99L) ?: 1L,
              notes,
            )
            showAddBringDialog = false
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("confirm_add_always_bring_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddBringDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showAddDoDialog) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("Before leaving home on every trip") }

    AlertDialog(
      onDismissRequest = { showAddDoDialog = false },
      title = { Text("Add Always-Do Task") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Task name (e.g. Empty the trash)") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("always_do_name_input"),
          )
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes / Reminder") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            ALWAYS_DO_SUGGESTIONS.take(4).forEach { suggestion ->
              SuggestionChip(
                onClick = { name = suggestion },
                label = { Text(suggestion, maxLines = 1) },
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onAddAlwaysDoTask(name, notes)
            showAddDoDialog = false
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("confirm_add_always_do_button"),
        ) {
          Text("Save Task")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDoDialog = false }) {
          Text("Cancel")
        }
      },
    )
  }

  editingItem?.let { item ->
    var itemName by remember(item.id) { mutableStateOf(item.name) }
    var selectedType by remember(item.id) {
      mutableStateOf(if (item.isAlwaysDo) VaultItemType.ALWAYS_DO else VaultItemType.ALWAYS_BRING)
    }
    var category by remember(item.id) {
      mutableStateOf(if (item.isAlwaysDo) "Essentials" else item.category.ifBlank { "Essentials" })
    }
    var qtyText by remember(item.id) { mutableStateOf(item.quantity.coerceIn(1L, 99L).toString()) }
    var notes by remember(item.id) { mutableStateOf(item.notes) }

    AlertDialog(
      onDismissRequest = { editingItem = null },
      title = {
        Text(
          if (selectedType == VaultItemType.ALWAYS_DO) "Edit Vault Task" else "Edit Vault Item"
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
              Text(if (selectedType == VaultItemType.ALWAYS_DO) "Task name" else "Item name")
            },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_vault_item_name_input"),
          )

          if (selectedType == VaultItemType.ALWAYS_BRING) {
            OutlinedTextField(
              value = qtyText,
              onValueChange = { qtyText = it.filter { ch -> ch.isDigit() } },
              label = { Text("Quantity") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_vault_item_qty_input"),
            )
          }

          Text(
            text = "Choose Vault Section",
            style = MaterialTheme.typography.labelLarge,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = selectedType == VaultItemType.ALWAYS_BRING,
              onClick = { selectedType = VaultItemType.ALWAYS_BRING },
              label = { Text("Always Bring") },
              modifier = Modifier.weight(1f),
            )
            FilterChip(
              selected = selectedType == VaultItemType.ALWAYS_DO,
              onClick = { selectedType = VaultItemType.ALWAYS_DO },
              label = { Text("Always-Do") },
              modifier = Modifier.weight(1f),
            )
          }

          if (selectedType == VaultItemType.ALWAYS_BRING) {
            Text(
              text = "Category",
              style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              availableCategories.forEach { cat ->
                FilterChip(
                  selected = category.equals(cat, ignoreCase = true),
                  onClick = { category = cat },
                  label = { Text(cat) },
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
          } else {
            Text(
              text = "Will be grouped under Always-Do (home tasks before leaving) across your vault and active trips.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes / Reminder") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onRecategorizeVaultItem(
              item,
              selectedType,
              category,
              notes,
              itemName.trim(),
              qtyText.toLongOrNull()?.coerceIn(1L, 99L) ?: 1L,
            )
            editingItem = null
          },
          enabled = itemName.isNotBlank(),
          modifier = Modifier.testTag("confirm_recategorize_vault_button"),
        ) {
          Text("Save Changes")
        }
      },
      dismissButton = {
        TextButton(onClick = { editingItem = null }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showManageCategoriesDialog) {
    val activeCounts = remember(bringItems, availableCategories) {
      val countsMap = bringItems
        .groupBy { it.category.trim().ifBlank { "Essentials" } }
        .mapValues { it.value.size }
      availableCategories.map { name ->
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
    val itemCount = bringItems.count {
      it.category.trim().ifBlank { "Essentials" }.equals(targetCat, ignoreCase = true)
    }
    EditCategoryDialog(
      categoryName = targetCat,
      itemCount = itemCount,
      availableTargets = availableCategories,
      onRenameOrMove = { newCat ->
        onRenameVaultCategory(targetCat, newCat, false)
      },
      onRemoveCategory = { fallback ->
        onRenameVaultCategory(targetCat, fallback, true)
      },
      onDismiss = { editingCategoryName = null },
    )
  }
}

@Composable
private fun VaultItemCard(
  item: AlwaysBringItem,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (!item.isAlwaysDo && item.quantity > 1L) "${item.name} ×${item.quantity}" else item.name,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
          text = "${item.category} · ${item.notes}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(
        onClick = onEdit,
        modifier = Modifier.testTag("recategorize_vault_${item.id}"),
      ) {
        Icon(
          Icons.Rounded.Edit,
          contentDescription = "Edit ${item.name}",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      IconButton(
        onClick = onDelete,
        modifier = Modifier.testTag("delete_always_bring_${item.id}"),
      ) {
        Icon(
          Icons.Rounded.DeleteOutline,
          contentDescription = "Remove ${item.name}",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
