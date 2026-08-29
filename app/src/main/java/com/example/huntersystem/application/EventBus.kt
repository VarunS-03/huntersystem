package com.example.huntersystem.application

import com.example.huntersystem.domain.events.DomainEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.CopyOnWriteArrayList

class EventBus {
  private val _events = MutableSharedFlow<DomainEvent>(extraBufferCapacity = 64)
  val events: SharedFlow<DomainEvent> = _events.asSharedFlow()

  private val listeners = CopyOnWriteArrayList<(DomainEvent) -> Unit>()

  fun registerListener(listener: (DomainEvent) -> Unit): () -> Unit {
    listeners.add(listener)
    return { listeners.remove(listener) }
  }

  fun emit(event: DomainEvent) {
    listeners.forEach { listener ->
      try {
        listener(event)
      } catch (_: Throwable) {
        // Listener exceptions must not crash event bus transport
      }
    }
    _events.tryEmit(event)
  }

  fun emitAll(events: List<DomainEvent>) {
    events.forEach { emit(it) }
  }
}
