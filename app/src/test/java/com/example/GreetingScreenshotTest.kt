package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.huntersystem.presentation.screens.DashboardShellScreen
import com.example.huntersystem.presentation.theme.HunterSystemTheme
import com.example.huntersystem.state.AppState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun captureDashboardShell() {
    val state = AppState.createDefault()
    composeRule.setContent {
      HunterSystemTheme {
        DashboardShellScreen(
          state = state,
          onDispatch = {}
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/dashboard_shell.png")
  }
}
