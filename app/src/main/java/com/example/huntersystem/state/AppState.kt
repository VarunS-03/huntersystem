package com.example.huntersystem.state

import com.example.huntersystem.domain.model.Achievements
import com.example.huntersystem.domain.model.DailyRecord
import com.example.huntersystem.domain.model.Metadata
import com.example.huntersystem.domain.model.Profile
import com.example.huntersystem.domain.model.Progression
import com.example.huntersystem.domain.model.Quest
import com.example.huntersystem.domain.model.Settings
import com.example.huntersystem.domain.model.Streak

data class QuestsState(
  val byId: Map<String, Quest> = emptyMap(),
  val order: List<String> = emptyList()
)

data class AppState(
  val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
  val profile: Profile,
  val progression: Progression,
  val quests: QuestsState = QuestsState(),
  val streak: Streak,
  val daily: DailyRecord,
  val achievements: Achievements = Achievements(),
  val settings: Settings = Settings(),
  val metadata: Metadata = Metadata()
) {
  companion object {
    const val CURRENT_SCHEMA_VERSION = 1

    fun createDefault(
      profileId: String = "hunter_default",
      displayName: String = "Sung Jinwoo",
      currentDate: String = "2026-08-28",
      currentTimestamp: String = "2026-08-28T10:00:00Z"
    ): AppState {
      return AppState(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        profile = Profile(
          id = profileId,
          displayName = displayName,
          createdAt = currentTimestamp,
          updatedAt = currentTimestamp
        ),
        progression = Progression(
          xp = 0,
          level = 1,
          gold = 0,
          rank = "E",
          totalCompleted = 0,
          totalFailed = 0
        ),
        quests = QuestsState(),
        streak = Streak(
          current = 0,
          best = 0,
          lastActivityDate = null,
          shieldCount = 0
        ),
        daily = DailyRecord(
          activeDate = currentDate,
          loginDates = listOf(currentDate),
          completedCount = 0,
          failedCount = 0,
          earnedXp = 0,
          earnedGold = 0
        ),
        achievements = Achievements(),
        settings = Settings(),
        metadata = Metadata(
          lastSavedAt = null,
          lastEventId = null,
          revision = 1L
        )
      )
    }
  }
}
