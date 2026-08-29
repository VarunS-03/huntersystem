package com.example.huntersystem.domain.engine

import com.example.huntersystem.domain.model.AchievementCatalog
import com.example.huntersystem.domain.model.Achievements
import com.example.huntersystem.state.AppState

data class AchievementEvaluationResult(
  val updatedAchievements: Achievements,
  val newlyUnlockedIds: List<String>
)

object AchievementEngine {

  /**
   * Pure, deterministic evaluation of achievements against candidate AppState.
   * Compares candidate state against the achievement catalog definitions.
   * Only triggers an unlock if the achievement has not already been unlocked.
   */
  fun evaluate(
    candidateState: AppState,
    timestamp: String
  ): AchievementEvaluationResult {
    val currentAchievements = candidateState.achievements
    val newlyUnlocked = mutableListOf<String>()

    for (definition in AchievementCatalog.ALL) {
      if (currentAchievements.unlockedIds.contains(definition.id)) {
        continue
      }

      val conditionMet = when (definition.id) {
        AchievementCatalog.FIRST_BLOOD.id -> {
          candidateState.progression.totalCompleted >= 1
        }
        AchievementCatalog.QUEST_HUNTER.id -> {
          candidateState.progression.totalCompleted >= 10
        }
        AchievementCatalog.AWAKENED.id -> {
          candidateState.progression.level >= 5
        }
        AchievementCatalog.RISING_HUNTER.id -> {
          candidateState.progression.level >= 10
        }
        AchievementCatalog.THREE_DAY_STREAK.id -> {
          candidateState.streak.current >= 3 || candidateState.streak.best >= 3
        }
        AchievementCatalog.SEVEN_DAY_STREAK.id -> {
          candidateState.streak.current >= 7 || candidateState.streak.best >= 7
        }
        AchievementCatalog.FIRST_REWARD.id -> {
          candidateState.progression.gold > 0
        }
        else -> false
      }

      if (conditionMet) {
        newlyUnlocked.add(definition.id)
      }
    }

    if (newlyUnlocked.isEmpty()) {
      return AchievementEvaluationResult(
        updatedAchievements = currentAchievements,
        newlyUnlockedIds = emptyList()
      )
    }

    val updatedUnlockedIds = currentAchievements.unlockedIds + newlyUnlocked
    val updatedTimestamps = currentAchievements.unlockedTimestamps + newlyUnlocked.associateWith { timestamp }

    return AchievementEvaluationResult(
      updatedAchievements = Achievements(
        unlockedIds = updatedUnlockedIds,
        unlockedTimestamps = updatedTimestamps
      ),
      newlyUnlockedIds = newlyUnlocked
    )
  }
}
