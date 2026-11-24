package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
  primary = TealPrimary,
  onPrimary = TealOnPrimary,
  primaryContainer = TealPrimaryContainer,
  onPrimaryContainer = TealOnPrimaryContainer,
  secondary = AmberSecondary,
  onSecondary = AmberOnSecondary,
  secondaryContainer = AmberSecondaryContainer,
  onSecondaryContainer = AmberOnSecondaryContainer,
  tertiary = CoralTertiary,
  onTertiary = CoralOnTertiary,
  tertiaryContainer = CoralTertiaryContainer,
  onTertiaryContainer = CoralOnTertiaryContainer,
  background = SandBackground,
  onBackground = DeepSlate,
  surface = SandSurface,
  onSurface = DeepSlate,
  surfaceContainerLow = SandSurfaceLow,
  surfaceContainerHighest = SandSurfaceHigh,
  onSurfaceVariant = MutedSlate,
  outlineVariant = OutlineStone,
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF7AD6DF),
  onPrimary = Color(0xFF00363B),
  primaryContainer = Color(0xFF004F55),
  onPrimaryContainer = TealPrimaryContainer,
  secondary = Color(0xFFFFB870),
  onSecondary = Color(0xFF4A2800),
  secondaryContainer = Color(0xFF6A3B00),
  onSecondaryContainer = AmberSecondaryContainer,
  tertiary = Color(0xFFFFB4A3),
  onTertiary = Color(0xFF621402),
  tertiaryContainer = Color(0xFF8C2D16),
  onTertiaryContainer = CoralTertiaryContainer,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceContainerLow = DarkSurfaceLow,
  surfaceContainerHighest = DarkSurfaceHigh,
  onSurfaceVariant = DarkMuted,
  outlineVariant = DarkOutline,
)

private val PackWiseShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(18.dp),
  extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun PackWiseTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = PackWiseTypography,
    shapes = PackWiseShapes,
    content = content,
  )
}
