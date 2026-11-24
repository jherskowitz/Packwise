package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.HomeBasePreferences

@Composable
fun DestinationHeaderImage(
  headerImageUrl: String,
  destination: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val fallbackPainter = painterResource(id = R.drawable.img_weather_pack)
  val request = remember(headerImageUrl) {
    ImageRequest.Builder(context)
      .data(headerImageUrl.ifBlank { null })
      .addHeader("User-Agent", "PackWiseAndroid/1.4 (Travel Packing App)")
      .crossfade(true)
      .build()
  }

  AsyncImage(
    model = request,
    contentDescription = "Photo of $destination",
    placeholder = fallbackPainter,
    error = fallbackPainter,
    fallback = fallbackPainter,
    contentScale = ContentScale.Crop,
    modifier = modifier,
  )
}

@Composable
fun HomeCountryPickerDialog(
  currentCountryCode: String,
  onSelectCountry: (code: String, name: String) -> Unit,
  onResetToDeviceRegion: () -> Unit,
  onDismiss: () -> Unit,
) {
  val allCountries = remember { HomeBasePreferences.allCountries() }
  var searchQuery by remember { mutableStateOf("") }
  val filtered = remember(searchQuery, allCountries) {
    val q = searchQuery.trim()
    if (q.isBlank()) {
      allCountries
    } else {
      allCountries.filter { (code, name) ->
        name.contains(q, ignoreCase = true) || code.equals(q, ignoreCase = true)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Home Country") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Power adapters, eSIMs, and entry tips are only suggested when a trip is outside your home country.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          label = { Text("Search country") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_country_search_input"),
        )
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        ) {
          items(filtered, key = { it.first }) { (code, name) ->
            val selected = code.equals(currentCountryCode, ignoreCase = true)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable {
                  onSelectCountry(code, name)
                  onDismiss()
                }
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .testTag("country_option_$code"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "$name ($code)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
              )
              if (selected) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          onResetToDeviceRegion()
          onDismiss()
        },
        modifier = Modifier.testTag("country_reset_device_button"),
      ) {
        Text("Use Device Region")
      }
    },
  )
}
