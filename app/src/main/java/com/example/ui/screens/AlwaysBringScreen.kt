package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AlwaysBringItem
import com.example.data.model.UiState
import com.example.ui.viewmodel.PackWiseViewModel

@Composable
fun AlwaysBringScreen(
  alwaysBringState: UiState<List<AlwaysBringItem>>,
  viewModel: PackWiseViewModel,
  onOpenGoogleTasksImport: () -> Unit,
) {
  var showAddDialog by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier.fillMaxSize(),
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
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  Icons.Rounded.Star,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Always Bring Vault",
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  text = "Auto-added to every new trip & reset unchecked after each journey",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("always_bring_add_button"),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Essential")
              }
              OutlinedButton(
                onClick = onOpenGoogleTasksImport,
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("always_bring_import_tasks_button"),
              ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Google Tasks")
              }
            }
          }
        }
      }

      when (alwaysBringState) {
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
              text = alwaysBringState.message,
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        is UiState.Success -> {
          val grouped = alwaysBringState.data.groupBy { it.category }.toSortedMap()
          if (grouped.isEmpty()) {
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "Your Always Bring vault is empty. Add staples like passport, chargers, or medications so they're never forgotten.",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.padding(16.dp),
                )
              }
            }
          } else {
            grouped.forEach { (category, items) ->
              item(key = "ab_cat_$category") {
                Text(
                  text = category,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.padding(top = 4.dp),
                )
              }
              items(items, key = { it.id }) { item ->
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
                      .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = if (item.quantity > 1L) "${item.name} ×${item.quantity}" else item.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                      )
                      if (item.notes.isNotBlank()) {
                        Text(
                          text = item.notes,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                    Surface(
                      shape = RoundedCornerShape(999.dp),
                      color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                      Text(
                        text = item.source.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                      onClick = { viewModel.deleteAlwaysBringItem(item.id) },
                      modifier = Modifier.size(44.dp),
                    ) {
                      Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Always Bring item",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
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

  if (showAddDialog) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Essentials") }
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Always Bring Essential") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item name") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ab_dialog_name_input"),
          )
          OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category (e.g., Documents, Tech)") },
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
            label = { Text("Notes (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val qty = qtyText.toLongOrNull()?.coerceIn(1L, 99L) ?: 1L
            viewModel.addAlwaysBringItem(name, category, qty, notes)
            showAddDialog = false
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("ab_dialog_confirm_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
      },
    )
  }
}
