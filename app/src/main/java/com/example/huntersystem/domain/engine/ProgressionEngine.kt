package com.example.huntersystem.domain.engine

import com.example.huntersystem.domain.model.Progression
import kotlin.math.pow
import kotlin.math.roundToInt

data class ProgressionCalculationResult(
  val updatedProgression: Progression,
  val levelsGained: Int,
  val oldLevel: Int,
  val newLevel: Int,
  val oldRank: String,
  val newRank: String,
  val rankPromoted: Boolean
)

object ProgressionEngine {

  fun xpRequiredForLevel(level: Int): Int {
    if (level < 1) return 100
    return (100 * level.toDouble().pow(1.25)).roundToInt()
  }

  fun rankForLevel(level: Int): String {
    return when {
      level >= 50 -> "S"
      level >= 30 -> "A"
      level >= 20 -> "B"
      level >= 10 -> "C"
      level >= 5 -> "D"
      else -> "E"
    }
  }

  fun applyXpGain(
    currentProgression: Progression,
    xpGained: Int,
    questCompleted: Boolean = false
  ): ProgressionCalculationResult {
    val initialLevel = currentProgression.level
    val initialRank = currentProgression.rank

    var level = initialLevel
    var currentXp = currentProgression.xp + xpGained.coerceAtLeast(0)

    while (true) {
      val requiredXp = xpRequiredForLevel(level)
      if (currentXp >= requiredXp) {
        currentXp -= requiredXp
        level++
      } else {
        break
      }
    }

    val newRank = rankForLevel(level)
    val levelsGained = level - initialLevel
    val rankPromoted = newRank != initialRank

    val updatedProgression = currentProgression.copy(
      xp = currentXp,
      level = level,
      rank = newRank,
      totalCompleted = if (questCompleted) currentProgression.totalCompleted + 1 else currentProgression.totalCompleted
    )

    return ProgressionCalculationResult(
      updatedProgression = updatedProgression,
      levelsGained = levelsGained,
      oldLevel = initialLevel,
      newLevel = level,
      oldRank = initialRank,
      newRank = newRank,
      rankPromoted = rankPromoted
    )
  }

  fun recordQuestFailure(currentProgression: Progression): Progression {
    return currentProgression.copy(
      totalFailed = currentProgression.totalFailed + 1
    )
  }
}
