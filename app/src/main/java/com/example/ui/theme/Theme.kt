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
  primary = DeepTeal,
  onPrimary = Color.White,
  primaryContainer = DeepTealLight,
  onPrimaryContainer = DeepTealDark,
  secondary = TerracottaAmber,
  onSecondary = Color.White,
  secondaryContainer = TerracottaAmberLight,
  onSecondaryContainer = TerracottaAmberDark,
  tertiary = SageEmerald,
  onTertiary = Color.White,
  tertiaryContainer = SageEmeraldLight,
  onTertiaryContainer = SageEmeraldDark,
  background = WarmAlabaster,
  onBackground = SlateInk,
  surface = WarmAlabaster,
  onSurface = SlateInk,
  surfaceVariant = WarmStone,
  onSurfaceVariant = SlateMuted,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color.White,
  surfaceContainer = Color(0xFFF4F0E8),
  surfaceContainerHigh = Color(0xFFEAE4D8),
  outline = WarmOutline,
  outlineVariant = WarmStoneBorder,
)

private val DarkColorScheme = darkColorScheme(
  primary = DarkSeafoam,
  onPrimary = Color(0xFF032A33),
  primaryContainer = DarkSeafoamContainer,
  onPrimaryContainer = Color(0xFFC4F0F7),
  secondary = DarkAmber,
  onSecondary = Color(0xFF3E1702),
  secondaryContainer = DarkAmberContainer,
  onSecondaryContainer = TerracottaAmberLight,
  tertiary = DarkSage,
  onTertiary = Color(0xFF032C26),
  tertiaryContainer = DarkSageContainer,
  onTertiaryContainer = SageEmeraldLight,
  background = DarkObsidian,
  onBackground = DarkInk,
  surface = DarkObsidian,
  onSurface = DarkInk,
  surfaceVariant = Color(0xFF232E33),
  onSurfaceVariant = DarkMuted,
  surfaceContainerLowest = DarkObsidian,
  surfaceContainerLow = DarkSurfaceLow,
  surfaceContainer = DarkSurface,
  surfaceContainerHigh = DarkSurfaceHigh,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
)

val PackWiseShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PackWiseTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = PackWiseTypography,
    shapes = PackWiseShapes,
    content = content,
  )
}
