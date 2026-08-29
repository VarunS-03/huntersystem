package com.example.huntersystem.domain.events

import com.example.huntersystem.domain.model.Quest
import com.example.huntersystem.domain.model.QuestReward

sealed interface DomainEvent {
  val id: String
  val occurredAt: String

  data class ProfileCreated(
    override val id: String,
    override val occurredAt: String,
    val profileId: String,
    val displayName: String
  ) : DomainEvent

  data class ProfileUpdated(
    override val id: String,
    override val occurredAt: String,
    val profileId: String,
    val displayName: String
  ) : DomainEvent

  data class ProfileReset(
    override val id: String,
    override val occurredAt: String
  ) : DomainEvent

  data class StateLoaded(
    override val id: String,
    override val occurredAt: String,
    val revision: Long
  ) : DomainEvent

  data class QuestCreated(
    override val id: String,
    override val occurredAt: String,
    val quest: Quest
  ) : DomainEvent

  data class QuestStarted(
    override val id: String,
    override val occurredAt: String,
    val questId: String
  ) : DomainEvent

  data class QuestCompleted(
    override val id: String,
    override val occurredAt: String,
    val questId: String,
    val reward: QuestReward
  ) : DomainEvent

  data class QuestFailed(
    override val id: String,
    override val occurredAt: String,
    val questId: String
  ) : DomainEvent

  data class QuestCancelled(
    override val id: String,
    override val occurredAt: String,
    val questId: String
  ) : DomainEvent

  data class QuestAbandoned(
    override val id: String,
    override val occurredAt: String,
    val questId: String
  ) : DomainEvent

  data class QuestDeleted(
    override val id: String,
    override val occurredAt: String,
    val questId: String
  ) : DomainEvent

  data class XpGained(
    override val id: String,
    override val occurredAt: String,
    val amount: Int,
    val totalXp: Int,
    val currentLevel: Int
  ) : DomainEvent

  data class LevelUp(
    override val id: String,
    override val occurredAt: String,
    val oldLevel: Int,
    val newLevel: Int
  ) : DomainEvent

  data class RankPromoted(
    override val id: String,
    override val occurredAt: String,
    val oldRank: String,
    val newRank: String
  ) : DomainEvent

  data class StreakChanged(
    override val id: String,
    override val occurredAt: String,
    val previousStreak: Int,
    val currentStreak: Int
  ) : DomainEvent

  data class StreakStarted(
    override val id: String,
    override val occurredAt: String,
    val date: String,
    val streakCount: Int
  ) : DomainEvent

  data class StreakContinued(
    override val id: String,
    override val occurredAt: String,
    val date: String,
    val streakCount: Int,
    val isBest: Boolean
  ) : DomainEvent

  data class StreakBroken(
    override val id: String,
    override val occurredAt: String,
    val date: String,
    val previousStreak: Int
  ) : DomainEvent

  data class DayStarted(
    override val id: String,
    override val occurredAt: String,
    val activeDate: String
  ) : DomainEvent

  data class GoldEarned(
    override val id: String,
    override val occurredAt: String,
    val amount: Int,
    val resultingBalance: Int,
    val source: String
  ) : DomainEvent

  data class GoldPenaltyApplied(
    override val id: String,
    override val occurredAt: String,
    val amount: Int,
    val resultingBalance: Int,
    val source: String
  ) : DomainEvent

  data class AchievementUnlocked(
    override val id: String,
    override val occurredAt: String,
    val achievementId: String
  ) : DomainEvent

  data class SettingsUpdated(
    override val id: String,
    override val occurredAt: String
  ) : DomainEvent

  data class PersistenceFailed(
    override val id: String,
    override val occurredAt: String,
    val reason: String
  ) : DomainEvent
}
