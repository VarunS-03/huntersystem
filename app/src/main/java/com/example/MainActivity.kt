package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.huntersystem.presentation.screens.DashboardShellScreen
import com.example.huntersystem.presentation.theme.HunterSystemTheme
import com.example.huntersystem.presentation.viewmodel.HunterViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: HunterViewModel by viewModels {
    HunterViewModel.provideFactory(application)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      HunterSystemTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()
        DashboardShellScreen(
          state = state,
          onDispatch = { command -> viewModel.dispatch(command) }
        )
      }
    }
  }
}
