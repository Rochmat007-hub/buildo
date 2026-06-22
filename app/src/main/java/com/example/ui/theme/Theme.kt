package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SleekPrimary,
    primaryContainer = SleekPrimaryContainer,
    secondary = SleekPrimaryContainer,
    tertiary = SleekBadgeBg,
    background = SleekBackground,
    surface = SleekSurface,
    onPrimary = SleekSurface,
    onSecondary = SleekNavy,
    onBackground = SleekTextMain,
    onSurface = SleekTextMain,
    onSurfaceVariant = SleekTextSoft,
    outline = SleekOutline,
    error = AlertDanger
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SleekPrimary,
    primaryContainer = SleekPrimaryContainer,
    secondary = SleekPrimaryContainer,
    tertiary = SleekBadgeBg,
    background = SleekBackground,
    surface = SleekSurface,
    onPrimary = SleekSurface,
    onSecondary = SleekNavy,
    onBackground = SleekTextMain,
    onSurface = SleekTextMain,
    onSurfaceVariant = SleekTextSoft,
    outline = SleekOutline,
    error = AlertDanger
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Sleek interface design is a modern, light-dominant look
  dynamicColor: Boolean = false, // Disable device dynamic color to preserve premium customized colors
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
