package com.example.huntersystem.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = NeonCyan,
  onPrimary = BackgroundDark,
  primaryContainer = SurfaceVariantDark,
  onPrimaryContainer = NeonCyan,
  secondary = NeonBlue,
  onSecondary = TextPrimary,
  tertiary = HunterGold,
  onTertiary = BackgroundDark,
  background = BackgroundDark,
  onBackground = TextPrimary,
  surface = SurfaceDark,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = TextSecondary,
  error = DangerRed,
  onError = TextPrimary
)

@Composable
fun HunterSystemTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = DarkColorScheme,
    typography = Typography,
    content = content
  )
}
