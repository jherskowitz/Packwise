package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.model.AiItemRecommendation
import com.example.data.model.DraftPackingItem
import com.example.data.model.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecommendationsSheet(
  destination: String,
  recommendationsState: UiState<AiItemRecommendation>,
  onRefresh: () -> Unit,
  onAddItems: (List<DraftPackingItem>) -> Unit,
  onDismissItem: (String) -> Unit,
  onClearError: () -> Unit,
  onDismissSheet: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val recommendedItems = (recommendationsState as? UiState.Success)?.data?.items.orEmpty()
  val engineLabel = (recommendationsState as? UiState.Success)?.data?.engineLabel.orEmpty()

  var selectedKeys by remember(recommendationsState) {
    mutableStateOf<Set<String>>(emptySet())
  }

  val itemKey: (DraftPackingItem) -> String = { item ->
    item.name.trim().lowercase(Locale.ROOT)
  }

  ModalBottomSheet(
    onDismissRequest = onDismissSheet,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Rounded.AutoAwesome,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "AI Packing Recommendations",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "Review suggestions for $destination and pick what to add",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onRefresh,
          enabled = recommendationsState !is UiState.Loading,
          modifier = Modifier.testTag("ai_recommendations_refresh_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Refresh recommendations",
          )
        }
      }

      when (recommendationsState) {
        is UiState.Idle,
        is UiState.Loading,
        -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CircularProgressIndicator()
              Text(
                text = "Generating packing recommendations for $destination...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        is UiState.Error -> {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ai_recommendations_error_card"),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Could not load recommendations",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Text(
                text = recommendationsState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
              ) {
                TextButton(
                  onClick = {
                    onClearError()
                    onDismissSheet()
                  },
                ) {
                  Text("Close")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                  onClick = onRefresh,
                  modifier = Modifier.heightIn(min = 48.dp),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Retry")
                }
              }
            }
          }
        }

        is UiState.Success -> {
          if (recommendedItems.isEmpty()) {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_recommendations_empty_card"),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CheckCircle,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(32.dp),
                )
                Text(
                  text = "All caught up",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = "All current AI recommendations are already in your checklist or dismissed.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = onDismissSheet,
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Text("Close")
                  }
                  Button(
                    onClick = onRefresh,
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Get More")
                  }
                }
              }
            }
          } else {
            val allKeys = remember(recommendedItems) {
              recommendedItems.map(itemKey).toSet()
            }
            val allSelected = selectedKeys.size == allKeys.size && allKeys.isNotEmpty()

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  text = "${recommendedItems.size} item(s) suggested",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (engineLabel.isNotBlank()) {
                  Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                  ) {
                    Text(
                      text = engineLabel,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                  }
                }
              }
              TextButton(
                onClick = {
                  selectedKeys = if (allSelected) emptySet() else allKeys
                },
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("ai_recommendations_select_all_button"),
              ) {
                Text(if (allSelected) "Clear All" else "Select All")
              }
            }

            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              itemsIndexed(
                items = recommendedItems,
                key = { _, item -> itemKey(item) },
              ) { index, item ->
                val key = itemKey(item)
                val checked = key in selectedKeys
                Card(
                  shape = MaterialTheme.shapes.medium,
                  colors = CardDefaults.cardColors(
                    containerColor = if (checked) {
                      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                      MaterialTheme.colorScheme.surfaceContainerLow
                    },
                  ),
                  border = BorderStroke(
                    width = 1.dp,
                    color = if (checked) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.outlineVariant
                    },
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      selectedKeys = if (checked) selectedKeys - key else selectedKeys + key
                    }
                    .testTag("ai_rec_card_$index"),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Checkbox(
                      checked = checked,
                      onCheckedChange = { isChecked ->
                        selectedKeys = if (isChecked) selectedKeys + key else selectedKeys - key
                      },
                      modifier = Modifier.testTag("ai_rec_checkbox_$index"),
                    )
                    Column(
                      modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                      verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Text(
                          text = if (item.quantity > 1L) "${item.name} ×${item.quantity}" else item.name,
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                          shape = MaterialTheme.shapes.extraSmall,
                          color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                          Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          )
                        }
                      }
                      if (item.contextReason.isNotBlank()) {
                        Text(
                          text = item.contextReason,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                    TextButton(
                      onClick = {
                        selectedKeys = selectedKeys - key
                        onAddItems(listOf(item))
                      },
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ai_rec_add_single_$index"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                      )
                      Spacer(modifier = Modifier.width(2.dp))
                      Text("Add", maxLines = 1, softWrap = false)
                    }
                    IconButton(
                      onClick = {
                        selectedKeys = selectedKeys - key
                        onDismissItem(item.name)
                      },
                      modifier = Modifier.testTag("ai_rec_dismiss_$index"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss ${item.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                      )
                    }
                  }
                }
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedButton(
                onClick = onDismissSheet,
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("ai_rec_close_button"),
              ) {
                Text("Close")
              }
              val selectedList = recommendedItems.filter { itemKey(it) in selectedKeys }
              Button(
                onClick = {
                  onAddItems(selectedList)
                  selectedKeys = emptySet()
                  onDismissSheet()
                },
                enabled = selectedList.isNotEmpty(),
                modifier = Modifier
                  .weight(1.4f)
                  .heightIn(min = 48.dp)
                  .testTag("ai_rec_add_selected_button"),
              ) {
                Text(
                  text = if (selectedList.isEmpty()) "Select Items to Add" else "Add Selected (${selectedList.size})",
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
