package com.example.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GoogleTaskItemInfo
import com.example.data.model.UiState
import com.example.data.remote.GoogleWorkspaceRepository
import com.example.ui.viewmodel.PackWiseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoogleTasksImportSheet(
  viewModel: PackWiseViewModel,
  activeTripId: String?,
  activeTripName: String?,
  defaultToAlwaysBring: Boolean,
  initialSharedText: String? = null,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val listsState by viewModel.googleTaskListsState.collectAsStateWithLifecycle()
  val selectedListId by viewModel.selectedTaskListId.collectAsStateWithLifecycle()
  val tasksState by viewModel.googleTasksItemsState.collectAsStateWithLifecycle()

  var selectedTab by remember(initialSharedText) {
    mutableIntStateOf(if (!initialSharedText.isNullOrBlank()) 1 else 0)
  }
  var selectedTaskIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var importAsAlwaysBring by remember(defaultToAlwaysBring, activeTripId) {
    mutableStateOf(defaultToAlwaysBring || activeTripId.isNullOrBlank())
  }

  // Paste / File Import state
  var pastedText by remember(initialSharedText) {
    mutableStateOf(initialSharedText.orEmpty())
  }
  val parsedTasks = remember(pastedText) {
    GoogleWorkspaceRepository.parseTasksFromText(pastedText)
  }
  var selectedParsedIds by remember(parsedTasks) {
    mutableStateOf(parsedTasks.map { it.id }.toSet())
  }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
      }.onSuccess { text ->
        if (!text.isNullOrBlank()) {
          pastedText = text
        } else {
          viewModel.showToast("Selected file was empty.")
        }
      }.onFailure { err ->
        viewModel.showToast("Could not read file: ${err.localizedMessage}")
      }
    }
  }

  LaunchedEffect(tasksState) {
    if (tasksState is UiState.Success) {
      selectedTaskIds = (tasksState as UiState.Success).data.map { it.id }.toSet()
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.Checklist,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = "Import from Google Tasks",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "Sync directly via Google Tasks or paste / import an exported list",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      PrimaryTabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Google Account") },
          modifier = Modifier.testTag("tab_google_tasks_oauth"),
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Paste / File Import") },
          modifier = Modifier.testTag("tab_paste_tasks"),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (selectedTab == 0) {
        when (val ls = listsState) {
          is UiState.Idle -> {
            Text(
              text = "Authorize read-only access to pull your lists straight from Google Tasks, or import immediately via Paste / File below.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = {
                (context as? Activity)?.let { viewModel.connectAndLoadGoogleTasks(it) }
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("connect_google_tasks_button"),
            ) {
              Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Connect Google Tasks")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = { selectedTab = 1 },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("idle_paste_import_button"),
            ) {
              Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Paste or Import File (No OAuth)")
            }
          }
          is UiState.Loading -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Loading your Google Task lists...")
            }
          }
          is UiState.Error -> {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "Google Tasks OAuth Blocked",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = ls.message,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = {
                      (context as? Activity)?.let { viewModel.connectAndLoadGoogleTasks(it) }
                    },
                    modifier = Modifier.weight(1f),
                  ) {
                    Text("Retry OAuth")
                  }
                  Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("switch_to_paste_import_button"),
                  ) {
                    Text("Paste List Instead")
                  }
                }
              }
            }
          }
          is UiState.Success -> {
            val lists = ls.data
            if (lists.isEmpty()) {
              Text(
                text = "No task lists found on your Google account.",
                style = MaterialTheme.typography.bodyMedium,
              )
            } else {
              Text(
                text = "Select a Google Tasks list:",
                style = MaterialTheme.typography.labelLarge,
              )
              Spacer(modifier = Modifier.height(6.dp))
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                lists.forEach { listInfo ->
                  FilterChip(
                    selected = listInfo.id == selectedListId,
                    onClick = { viewModel.selectGoogleTaskList(listInfo.id) },
                    label = { Text(listInfo.title) },
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              ImportTargetToggle(
                activeTripId = activeTripId,
                activeTripName = activeTripName,
                importAsAlwaysBring = importAsAlwaysBring,
                onToggle = { importAsAlwaysBring = it },
              )

              when (val ts = tasksState) {
                is UiState.Loading -> {
                  CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
                is UiState.Error -> {
                  Text(ts.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                  SelectableTasksList(
                    tasks = ts.data,
                    selectedIds = selectedTaskIds,
                    onToggleId = { id ->
                      selectedTaskIds = if (id in selectedTaskIds) selectedTaskIds - id else selectedTaskIds + id
                    },
                    emptyMessage = "This list has no tasks.",
                    buttonTestTag = "confirm_import_google_tasks_button",
                    onImport = { toImport ->
                      viewModel.importGoogleTasks(
                        tasks = toImport,
                        targetTripId = activeTripId,
                        importAsAlwaysBring = importAsAlwaysBring,
                        onDone = onDismiss,
                      )
                    },
                  )
                }
                is UiState.Idle -> Unit
              }
            }
          }
        }
      } else {
        // Tab 1: Paste / File Import (zero OAuth blocks, zero storage permissions)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
              if (clip.isNotBlank()) {
                pastedText = clip
              } else {
                viewModel.showToast("Clipboard is empty. Copy items from Google Tasks first.")
              }
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("paste_from_clipboard_button"),
          ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Paste Clipboard", maxLines = 1)
          }
          OutlinedButton(
            onClick = {
              filePickerLauncher.launch(arrayOf("text/plain", "application/json", "text/csv", "*/*"))
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("pick_tasks_file_button"),
          ) {
            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Pick .txt / .json", maxLines = 1)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = pastedText,
          onValueChange = { pastedText = it },
          label = { Text("Tasks list (one item per line or Takeout JSON)") },
          placeholder = {
            Text("Passport\n- [ ] Rain jacket\n- [ ] Power bank - 10,000mAh")
          },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("paste_tasks_input"),
        )

        if (pastedText.isNotBlank()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Detected ${parsedTasks.size} item(s)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { pastedText = "" }) {
              Text("Clear")
            }
          }
        } else {
          Spacer(modifier = Modifier.height(10.dp))
        }

        ImportTargetToggle(
          activeTripId = activeTripId,
          activeTripName = activeTripName,
          importAsAlwaysBring = importAsAlwaysBring,
          onToggle = { importAsAlwaysBring = it },
        )

        if (parsedTasks.isNotEmpty()) {
          SelectableTasksList(
            tasks = parsedTasks,
            selectedIds = selectedParsedIds,
            onToggleId = { id ->
              selectedParsedIds = if (id in selectedParsedIds) selectedParsedIds - id else selectedParsedIds + id
            },
            emptyMessage = "",
            buttonTestTag = "confirm_import_pasted_tasks_button",
            onImport = { toImport ->
              viewModel.importGoogleTasks(
                tasks = toImport,
                targetTripId = activeTripId,
                importAsAlwaysBring = importAsAlwaysBring,
                onDone = onDismiss,
              )
            },
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
    }
  }
}

@Composable
private fun ImportTargetToggle(
  activeTripId: String?,
  activeTripName: String?,
  importAsAlwaysBring: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  if (activeTripId.isNullOrBlank()) return
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (importAsAlwaysBring) {
          "Import into Always Bring Vault"
        } else {
          "Import into ${activeTripName ?: "Selected Trip"}"
        },
        style = MaterialTheme.typography.labelLarge,
      )
      Text(
        text = "Toggle to choose trip-specific vs Always Bring",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = importAsAlwaysBring,
      onCheckedChange = onToggle,
    )
  }
  Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun SelectableTasksList(
  tasks: List<GoogleTaskItemInfo>,
  selectedIds: Set<String>,
  onToggleId: (String) -> Unit,
  emptyMessage: String,
  buttonTestTag: String,
  onImport: (List<GoogleTaskItemInfo>) -> Unit,
) {
  if (tasks.isEmpty()) {
    if (emptyMessage.isNotBlank()) {
      Text(
        text = emptyMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    return
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = 220.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items(tasks, key = { it.id }) { task ->
      val checked = task.id in selectedIds
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .clickable { onToggleId(task.id) }
          .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = checked,
          onCheckedChange = { onToggleId(task.id) },
        )
        Column {
          Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          )
          if (task.notes.isNotBlank()) {
            Text(
              text = task.notes,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }

  Spacer(modifier = Modifier.height(12.dp))

  val toImport = tasks.filter { it.id in selectedIds }
  Button(
    onClick = { onImport(toImport) },
    enabled = toImport.isNotEmpty(),
    modifier = Modifier
      .fillMaxWidth()
      .height(50.dp)
      .testTag(buttonTestTag),
  ) {
    Text("Import ${toImport.size} Item(s)")
  }
}
