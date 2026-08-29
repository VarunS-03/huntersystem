package com.example.huntersystem.domain.engine

import com.example.huntersystem.domain.model.Streak
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class StreakActionResult(
  val updatedStreak: Streak,
  val isStarted: Boolean,
  val isContinued: Boolean,
  val isIncremented: Boolean,
  val isBestNew: Boolean,
  val previousStreak: Int
)

data class StreakRolloverResult(
  val updatedStreak: Streak,
  val isBroken: Boolean,
  val previousStreak: Int
)

object StreakEngine {

  fun calculateDaysBetween(fromDateStr: String, toDateStr: String): Long {
    return try {
      val from = LocalDate.parse(fromDateStr)
      val to = LocalDate.parse(toDateStr)
      ChronoUnit.DAYS.between(from, to)
    } catch (_: Exception) {
      0L
    }
  }

  fun evaluateStreakOnRollover(currentStreak: Streak, currentDate: String): StreakRolloverResult {
    val lastDate = currentStreak.lastActivityDate
    if (lastDate == null) {
      return StreakRolloverResult(
        updatedStreak = currentStreak,
        isBroken = false,
        previousStreak = currentStreak.current
      )
    }

    val days = calculateDaysBetween(lastDate, currentDate)
    if (days > 1L && currentStreak.current > 0) {
      val brokenStreak = currentStreak.copy(current = 0)
      return StreakRolloverResult(
        updatedStreak = brokenStreak,
        isBroken = true,
        previousStreak = currentStreak.current
      )
    }

    return StreakRolloverResult(
      updatedStreak = currentStreak,
      isBroken = false,
      previousStreak = currentStreak.current
    )
  }

  fun recordQualifyingActivity(currentStreak: Streak, currentDate: String): StreakActionResult {
    val lastDate = currentStreak.lastActivityDate
    val prevCount = currentStreak.current

    if (lastDate == currentDate) {
      // Same-day idempotency: already counted today
      return StreakActionResult(
        updatedStreak = currentStreak,
        isStarted = false,
        isContinued = false,
        isIncremented = false,
        isBestNew = false,
        previousStreak = prevCount
      )
    }

    if (lastDate == null) {
      // First qualifying activity
      val newCurrent = 1
      val newBest = maxOf(currentStreak.best, newCurrent)
      val updated = currentStreak.copy(
        current = newCurrent,
        best = newBest,
        lastActivityDate = currentDate
      )
      return StreakActionResult(
        updatedStreak = updated,
        isStarted = true,
        isContinued = false,
        isIncremented = true,
        isBestNew = newBest > currentStreak.best,
        previousStreak = prevCount
      )
    }

    val days = calculateDaysBetween(lastDate, currentDate)
    if (days == 1L) {
      // Consecutive day continuation
      val newCurrent = currentStreak.current + 1
      val newBest = maxOf(currentStreak.best, newCurrent)
      val updated = currentStreak.copy(
        current = newCurrent,
        best = newBest,
        lastActivityDate = currentDate
      )
      return StreakActionResult(
        updatedStreak = updated,
        isStarted = false,
        isContinued = true,
        isIncremented = true,
        isBestNew = newBest > currentStreak.best,
        previousStreak = prevCount
      )
    }

    // Missed 1 or more days (days > 1) or clock reset -> starts fresh with 1
    val newCurrent = 1
    val newBest = maxOf(currentStreak.best, newCurrent)
    val updated = currentStreak.copy(
      current = newCurrent,
      best = newBest,
      lastActivityDate = currentDate
    )
    return StreakActionResult(
      updatedStreak = updated,
      isStarted = true,
      isContinued = false,
      isIncremented = true,
      isBestNew = newBest > currentStreak.best,
      previousStreak = prevCount
    )
  }
}
