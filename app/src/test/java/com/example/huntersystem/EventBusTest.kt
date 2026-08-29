package com.example.huntersystem

import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.events.DomainEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventBusTest {

  @Test
  fun testListenerReceivesEvents() {
    val bus = EventBus()
    val received = mutableListOf<DomainEvent>()

    val unregister = bus.registerListener { event ->
      received.add(event)
    }

    val testEvent = DomainEvent.ProfileReset(id = "evt_1", occurredAt = "2026-08-28T10:00:00Z")
    bus.emit(testEvent)

    assertEquals(1, received.size)
    assertEquals("evt_1", received[0].id)

    unregister()
    bus.emit(DomainEvent.ProfileReset(id = "evt_2", occurredAt = "2026-08-28T10:00:00Z"))
    assertEquals(1, received.size)
  }

  @Test
  fun testSharedFlowStream() = runTest {
    val bus = EventBus()
    val testEvent = DomainEvent.SettingsUpdated(id = "evt_flow", occurredAt = "2026-08-28T10:00:00Z")

    val deferred = async {
      bus.events.first()
    }

    // Yield to allow collector to start listening
    kotlinx.coroutines.yield()
    bus.emit(testEvent)

    val received = deferred.await()
    assertEquals("evt_flow", received.id)
  }
}
