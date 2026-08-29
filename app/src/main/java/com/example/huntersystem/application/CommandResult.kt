package com.example.huntersystem.application

import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.state.AppState

sealed interface CommandResult {
  data class Success(
    val state: AppState,
    val events: List<DomainEvent> = emptyList()
  ) : CommandResult

  data class Failure(
    val error: DomainError
  ) : CommandResult
}
