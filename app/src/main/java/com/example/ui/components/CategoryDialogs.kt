package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryPreferences
import com.example.data.model.CATEGORY_ALWAYS_DO

@Composable
fun CategorySectionHeader(
  categoryName: String,
  packedCount: Int?,
  totalCount: Int,
  isExpanded: Boolean,
  canEditCategory: Boolean,
  onToggleExpand: () -> Unit,
  onEditCategory: () -> Unit,
) {
  val allComplete = packedCount != null && totalCount > 0 && packedCount == totalCount
  val isAlwaysDo = categoryName.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = when {
      isAlwaysDo -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
      allComplete -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      else -> MaterialTheme.colorScheme.surfaceContainerHigh
    },
    border = BorderStroke(
      width = 1.dp,
      color = if (isAlwaysDo) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
      } else {
        MaterialTheme.colorScheme.outlineVariant
      },
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onToggleExpand)
      .testTag("category_header_${categoryName.lowercase().replace(' ', '_')}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = CategoryPreferences.iconForCategory(categoryName),
        contentDescription = null,
        tint = if (isAlwaysDo) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = categoryName,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Surface(
        shape = MaterialTheme.shapes.small,
        color = if (allComplete) {
          MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
          MaterialTheme.colorScheme.surfaceContainerLow
        },
      ) {
        Text(
          text = if (packedCount != null) "$packedCount/$totalCount" else "$totalCount",
          style = MaterialTheme.typography.labelMedium,
          color = if (allComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
      if (canEditCategory) {
        IconButton(
          onClick = onEditCategory,
          modifier = Modifier.testTag("edit_category_${categoryName.lowercase().replace(' ', '_')}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit $categoryName category",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
        }
      }
      IconButton(
        onClick = onToggleExpand,
        modifier = Modifier.testTag("toggle_category_${categoryName.lowercase().replace(' ', '_')}"),
      ) {
        Icon(
          imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = if (isExpanded) "Collapse $categoryName" else "Expand $categoryName",
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCategoryDialog(
  categoryName: String,
  itemCount: Int,
  availableTargets: List<String>,
  onRenameOrMove: (newCategory: String) -> Unit,
  onRemoveCategory: (fallbackCategory: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var editedName by remember(categoryName) { mutableStateOf(categoryName) }
  val otherTargets = remember(availableTargets, categoryName) {
    availableTargets.filterNot { it.equals(categoryName, ignoreCase = true) }
  }
  val fallbackTarget = otherTargets.firstOrNull { it.equals("Essentials", ignoreCase = true) }
    ?: otherTargets.firstOrNull()
    ?: "Essentials"

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Category") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Rename \"$categoryName\" or merge its $itemCount item(s) into another category.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = editedName,
          onValueChange = { editedName = it },
          label = { Text("Category name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_category_name_input"),
        )
        if (otherTargets.isNotEmpty()) {
          Text(
            text = "Or merge into existing category:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            otherTargets.take(8).forEach { target ->
              FilterChip(
                selected = editedName.equals(target, ignoreCase = true),
                onClick = { editedName = target },
                label = { Text(target) },
              )
            }
          }
        }
        if (itemCount > 0 && !categoryName.equals("Essentials", ignoreCase = true)) {
          OutlinedButton(
            onClick = {
              onRemoveCategory(fallbackTarget)
              onDismiss()
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("remove_category_move_items_button"),
          ) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Remove Category (Move to $fallbackTarget)",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onRenameOrMove(editedName.trim())
          onDismiss()
        },
        enabled = editedName.trim().isNotBlank() &&
          !editedName.trim().equals(CATEGORY_ALWAYS_DO, ignoreCase = true),
        modifier = Modifier.testTag("confirm_edit_category_button"),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
fun ManageCategoriesDialog(
  categoryCounts: List<Pair<String, Int>>,
  customCategories: List<String>,
  onAddCustomCategory: (String) -> Unit,
  onEditCategory: (String) -> Unit,
  onDeleteCustomCategory: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var newCategoryName by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Manage Categories") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Create custom categories or rename existing ones. Moving or removing a category keeps all your items intact.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newCategoryName,
            onValueChange = { newCategoryName = it },
            label = { Text("New category") },
            placeholder = { Text("e.g., Camera Gear") },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .testTag("manage_new_category_input"),
          )
          Button(
            onClick = {
              onAddCustomCategory(newCategoryName.trim())
              newCategoryName = ""
            },
            enabled = newCategoryName.trim().isNotBlank() &&
              !newCategoryName.trim().equals(CATEGORY_ALWAYS_DO, ignoreCase = true),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("manage_add_category_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add", maxLines = 1, softWrap = false)
          }
        }

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        ) {
          itemsIndexed(categoryCounts, key = { _, pair -> pair.first.lowercase() }) { index, (catName, count) ->
            val isAlwaysDo = catName.equals(CATEGORY_ALWAYS_DO, ignoreCase = true)
            val isCustom = customCategories.any { it.equals(catName, ignoreCase = true) }
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = CategoryPreferences.iconForCategory(catName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = catName,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = when {
                    isAlwaysDo -> "$count item(s) · Pre-departure tasks"
                    count > 0 -> "$count item(s)"
                    else -> "Saved custom category"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (!isAlwaysDo) {
                IconButton(
                  onClick = { onEditCategory(catName) },
                  modifier = Modifier.testTag("manage_edit_category_$index"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit $catName",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
                if (isCustom && count == 0) {
                  IconButton(
                    onClick = { onDeleteCustomCategory(catName) },
                    modifier = Modifier.testTag("manage_delete_category_$index"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.DeleteOutline,
                      contentDescription = "Delete $catName",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Done")
      }
    },
  )
}
