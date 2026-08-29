package com.example.huntersystem.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.data.repository.JsonFileProfileRepository
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.ClockPort
import com.example.huntersystem.infrastructure.IdGenerator
import com.example.huntersystem.infrastructure.SystemClockPort
import com.example.huntersystem.infrastructure.UuidGenerator
import com.example.huntersystem.state.AppState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HunterViewModel(
  application: Application,
  private val controller: AppController,
  private val eventBus: EventBus
) : AndroidViewModel(application) {

  val state: StateFlow<AppState> = controller.state
  val events: SharedFlow<DomainEvent> = eventBus.events

  private val _commandErrors = MutableSharedFlow<String>(extraBufferCapacity = 16)
  val commandErrors: SharedFlow<String> = _commandErrors.asSharedFlow()

  init {
    viewModelScope.launch {
      controller.dispatch(Command.LoadState)
      controller.dispatch(Command.ResolveDailyCycle)
    }
  }

  fun dispatch(command: Command) {
    viewModelScope.launch {
      val result = controller.dispatch(command)
      if (result is CommandResult.Failure) {
        _commandErrors.emit(result.error.message)
      }
    }
  }

  companion object {
    fun provideFactory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          val repository: ProfileRepository = JsonFileProfileRepository(application.filesDir)
          val eventBus = EventBus()
          val clock: ClockPort = SystemClockPort()
          val idGenerator: IdGenerator = UuidGenerator()
          val controller = AppController(
            repository = repository,
            eventBus = eventBus,
            clock = clock,
            idGenerator = idGenerator
          )
          return HunterViewModel(application, controller, eventBus) as T
        }
      }
  }
}
