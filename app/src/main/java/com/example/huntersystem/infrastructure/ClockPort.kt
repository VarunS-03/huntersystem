package com.example.huntersystem.infrastructure

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

interface ClockPort {
  fun currentTimeMillis(): Long
  fun currentIsoTimestamp(): String
  fun currentLocalDate(): String
}

class SystemClockPort(
  private val timeZone: TimeZone = TimeZone.getDefault()
) : ClockPort {

  override fun currentTimeMillis(): Long = System.currentTimeMillis()

  override fun currentIsoTimestamp(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      this.timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(currentTimeMillis()))
  }

  override fun currentLocalDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      this.timeZone = this@SystemClockPort.timeZone
    }
    return sdf.format(Date(currentTimeMillis()))
  }
}

class TestClockPort(
  var fixedTimeMillis: Long = 1756375200000L, // 2025-08-28 10:00:00 UTC
  var fixedIsoTimestamp: String = "2025-08-28T10:00:00Z",
  var fixedLocalDate: String = "2025-08-28"
) : ClockPort {
  override fun currentTimeMillis(): Long = fixedTimeMillis
  override fun currentIsoTimestamp(): String = fixedIsoTimestamp
  override fun currentLocalDate(): String = fixedLocalDate

  fun setTime(millis: Long, isoTimestamp: String, localDate: String) {
    fixedTimeMillis = millis
    fixedIsoTimestamp = isoTimestamp
    fixedLocalDate = localDate
  }

  fun advanceTime(hours: Long = 0, minutes: Long = 0, seconds: Long = 0, millis: Long = 0) {
    val totalDeltaMillis = (hours * 3600 + minutes * 60 + seconds) * 1000 + millis
    fixedTimeMillis += totalDeltaMillis
    val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = Date(fixedTimeMillis)
    fixedIsoTimestamp = sdfIso.format(date)
    fixedLocalDate = sdfDate.format(date)
  }
}

interface IdGenerator {
  fun generateId(prefix: String = ""): String
}

class UuidGenerator : IdGenerator {
  override fun generateId(prefix: String): String {
    val raw = UUID.randomUUID().toString().replace("-", "").take(12)
    return if (prefix.isEmpty()) raw else "${prefix}_$raw"
  }
}

class DeterministicIdGenerator(private var counter: Long = 0L) : IdGenerator {
  override fun generateId(prefix: String): String {
    counter++
    val id = counter.toString().padStart(6, '0')
    return if (prefix.isEmpty()) id else "${prefix}_$id"
  }
}
