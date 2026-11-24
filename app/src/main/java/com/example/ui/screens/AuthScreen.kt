package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.onGoogleSignInClicked

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 520.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp)
              .clip(MaterialTheme.shapes.extraLarge),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_hero_journey),
              contentDescription = "Travel packing gear banner",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC062529)),
                  )
                ),
            )
            Column(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.Luggage,
                  contentDescription = null,
                  tint = Color(0xFFCDE9EC),
                  modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "PACKWISE",
                  style = MaterialTheme.typography.labelLarge,
                  color = Color(0xFFCDE9EC),
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Pack Smarter for Every Journey",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          Text(
            text = "Tailored checklists driven by trip length, live weather, and planned events.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(modifier = Modifier.height(16.dp))

          FeatureHighlightRow(
            icon = Icons.Rounded.AutoAwesome,
            title = "AI & Weather Aware",
            subtitle = "Scales outfits to trip days and adds rain, cold, or heat alerts automatically.",
          )
          Spacer(modifier = Modifier.height(10.dp))
          FeatureHighlightRow(
            icon = Icons.Rounded.RestartAlt,
            title = "Always Bring + Fresh Resets",
            subtitle = "Keep timeless essentials synced separately and reset trip lists fresh after every return.",
          )
          Spacer(modifier = Modifier.height(10.dp))
          FeatureHighlightRow(
            icon = Icons.Rounded.Checklist,
            title = "Google Tasks & Calendar Sync",
            subtitle = "Import existing checklists from Google Tasks and adapt lists to planned calendar events.",
          )
          Spacer(modifier = Modifier.height(10.dp))
          FeatureHighlightRow(
            icon = Icons.Rounded.CloudQueue,
            title = "Synced Across Trips",
            subtitle = "Sign in with Google to back up your trips and Always Bring vault securely.",
          )
        }

        Column(modifier = Modifier.padding(top = 20.dp)) {
          errorMessage?.let { msg ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            ) {
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = {
              isLoading = true
              errorMessage = null
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                  isLoading = false
                },
              )
            },
            enabled = !isLoading,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Signing in...")
            } else {
              Text(
                text = "Sign in with Google",
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

@Composable
private fun FeatureHighlightRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
      .padding(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .padding(top = 2.dp)
        .size(20.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
