package com.example.huntersystem

import com.example.huntersystem.infrastructure.DeterministicIdGenerator
import com.example.huntersystem.infrastructure.SystemClockPort
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.infrastructure.UuidGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class InfrastructurePortsTest {

  @Test
  fun testSystemClockPortFormats() {
    val clock = SystemClockPort(timeZone = TimeZone.getTimeZone("UTC"))
    val iso = clock.currentIsoTimestamp()
    val date = clock.currentLocalDate()

    assertTrue(iso.contains("T"))
    assertTrue(iso.endsWith("Z"))
    assertEquals(10, date.length) // YYYY-MM-DD
  }

  @Test
  fun testTestClockPort() {
    val clock = TestClockPort(
      fixedTimeMillis = 1000L,
      fixedIsoTimestamp = "2026-08-28T00:00:00Z",
      fixedLocalDate = "2026-08-28"
    )
    assertEquals(1000L, clock.currentTimeMillis())
    assertEquals("2026-08-28T00:00:00Z", clock.currentIsoTimestamp())
    assertEquals("2026-08-28", clock.currentLocalDate())
  }

  @Test
  fun testDeterministicIdGenerator() {
    val idGen = DeterministicIdGenerator()
    assertEquals("hunter_000001", idGen.generateId("hunter"))
    assertEquals("hunter_000002", idGen.generateId("hunter"))
  }

  @Test
  fun testUuidGenerator() {
    val idGen = UuidGenerator()
    val id1 = idGen.generateId("quest")
    val id2 = idGen.generateId("quest")
    assertTrue(id1.startsWith("quest_"))
    assertTrue(id2.startsWith("quest_"))
    assertTrue(id1 != id2)
  }
}
