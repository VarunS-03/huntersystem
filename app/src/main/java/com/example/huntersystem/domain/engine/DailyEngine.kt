package com.example.huntersystem.domain.engine

import com.example.huntersystem.domain.model.DailyRecord

data class DailyResolutionResult(
  val updatedDaily: DailyRecord,
  val isRollover: Boolean,
  val previousDate: String
)

object DailyEngine {

  fun resolveDay(currentDaily: DailyRecord, currentDate: String): DailyResolutionResult {
    if (currentDaily.activeDate == currentDate) {
      return DailyResolutionResult(
        updatedDaily = currentDaily,
        isRollover = false,
        previousDate = currentDaily.activeDate
      )
    }

    val updatedDaily = DailyRecord(
      activeDate = currentDate,
      loginDates = (currentDaily.loginDates + currentDate).distinct(),
      completedCount = 0,
      failedCount = 0,
      earnedXp = 0,
      earnedGold = 0
    )

    return DailyResolutionResult(
      updatedDaily = updatedDaily,
      isRollover = true,
      previousDate = currentDaily.activeDate
    )
  }

  fun recordQuestCompletion(
    currentDaily: DailyRecord,
    currentDate: String,
    xpGained: Int,
    goldGained: Int
  ): DailyRecord {
    val activeDaily = if (currentDaily.activeDate != currentDate) {
      resolveDay(currentDaily, currentDate).updatedDaily
    } else {
      currentDaily
    }

    return activeDaily.copy(
      completedCount = activeDaily.completedCount + 1,
      earnedXp = activeDaily.earnedXp + xpGained.coerceAtLeast(0),
      earnedGold = activeDaily.earnedGold + goldGained.coerceAtLeast(0)
    )
  }

  fun recordQuestFailure(
    currentDaily: DailyRecord,
    currentDate: String
  ): DailyRecord {
    val activeDaily = if (currentDaily.activeDate != currentDate) {
      resolveDay(currentDaily, currentDate).updatedDaily
    } else {
      currentDaily
    }

    return activeDaily.copy(
      failedCount = activeDaily.failedCount + 1
    )
  }
}
