package com.example.huntersystem.domain.repository

import com.example.huntersystem.state.AppState

interface ProfileRepository {
  suspend fun loadState(): Result<AppState>
  suspend fun saveState(state: AppState): Result<Unit>
  suspend fun clearState(): Result<Unit>
  suspend fun exportState(): Result<String>
  suspend fun importState(serialized: String): Result<AppState>
}
