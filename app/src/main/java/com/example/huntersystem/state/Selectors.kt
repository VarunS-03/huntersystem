package com.example.huntersystem.state

import com.example.huntersystem.domain.engine.ProgressionEngine
import com.example.huntersystem.domain.model.Quest
import com.example.huntersystem.domain.model.QuestStatus

object Selectors {

  fun xpRequiredForNextLevel(currentLevel: Int): Int {
    return ProgressionEngine.xpRequiredForLevel(currentLevel)
  }

  fun xpProgressPercent(currentXp: Int, currentLevel: Int): Float {
    val req = xpRequiredForNextLevel(currentLevel)
    if (req <= 0) return 0f
    return (currentXp.toFloat() / req.toFloat()).coerceIn(0f, 1f)
  }

  fun currentRank(level: Int): String {
    return ProgressionEngine.rankForLevel(level)
  }

  fun currentRank(state: AppState): String {
    return currentRank(state.progression.level)
  }

  // Daily & Streak Selectors
  fun isToday(date: String, currentDate: String): Boolean {
    return date == currentDate
  }

  fun dailyCompletedCount(state: AppState): Int = state.daily.completedCount

  fun dailyFailedCount(state: AppState): Int = state.daily.failedCount

  fun dailyEarnedXp(state: AppState): Int = state.daily.earnedXp

  fun dailyEarnedGold(state: AppState): Int = state.daily.earnedGold

  // Economy Selectors
  fun currentGold(state: AppState): Int = state.progression.gold

  fun canAfford(state: AppState, amount: Int): Boolean = amount >= 0 && state.progression.gold >= amount

  fun currentStreak(state: AppState): Int = state.streak.current

  fun bestStreak(state: AppState): Int = state.streak.best

  fun isStreakActiveToday(state: AppState, currentDate: String): Boolean {
    return state.streak.lastActivityDate == currentDate
  }

  fun isStreakAtRisk(state: AppState, currentDate: String): Boolean {
    val lastDate = state.streak.lastActivityDate ?: return false
    return state.streak.current > 0 && lastDate != currentDate
  }

  fun hasStreak(state: AppState): Boolean = state.streak.current > 0

  fun allQuests(state: AppState): List<Quest> {
    return state.quests.order.mapNotNull { id -> state.quests.byId[id] }
  }

  fun activeQuests(state: AppState): List<Quest> {
    return allQuests(state).filter { it.status == QuestStatus.ACTIVE }
  }

  fun pendingQuests(state: AppState): List<Quest> {
    return allQuests(state).filter { it.status == QuestStatus.PENDING }
  }

  fun completedQuests(state: AppState): List<Quest> {
    return allQuests(state).filter { it.status == QuestStatus.COMPLETED }
  }

  fun failedQuests(state: AppState): List<Quest> {
    return allQuests(state).filter { it.status == QuestStatus.FAILED }
  }

  fun cancelledQuests(state: AppState): List<Quest> {
    return allQuests(state).filter { it.status == QuestStatus.CANCELLED }
  }

  fun questById(state: AppState, id: String): Quest? {
    return state.quests.byId[id]
  }

  fun isQuestOverdue(quest: Quest, currentDate: String): Boolean {
    if (quest.status != QuestStatus.ACTIVE && quest.status != QuestStatus.PENDING) return false
    val due = quest.dueDate ?: return false
    return due < currentDate
  }

  fun nextActionCue(state: AppState): String {
    val active = activeQuests(state)
    val pending = pendingQuests(state)
    return when {
      active.isNotEmpty() -> "System Directive: ${active.size} active quest(s) awaiting execution."
      pending.isNotEmpty() -> "System Directive: ${pending.size} pending quest(s) awaiting initialization."
      else -> "System Directive: No active quests. Issue a new Hunter directive."
    }
  }

  // Achievement Selectors
  fun isAchievementUnlocked(state: AppState, achievementId: String): Boolean {
    return state.achievements.unlockedIds.contains(achievementId)
  }

  fun unlockedAchievementCount(state: AppState): Int {
    return state.achievements.unlockedIds.size
  }

  fun totalAchievementCount(): Int {
    return com.example.huntersystem.domain.model.AchievementCatalog.ALL.size
  }

  fun allAchievements(): List<com.example.huntersystem.domain.model.AchievementDefinition> {
    return com.example.huntersystem.domain.model.AchievementCatalog.ALL
  }

  fun unlockedAchievements(state: AppState): List<com.example.huntersystem.domain.model.AchievementDefinition> {
    return com.example.huntersystem.domain.model.AchievementCatalog.ALL.filter {
      state.achievements.unlockedIds.contains(it.id)
    }
  }

  fun lockedAchievements(state: AppState): List<com.example.huntersystem.domain.model.AchievementDefinition> {
    return com.example.huntersystem.domain.model.AchievementCatalog.ALL.filter {
      !state.achievements.unlockedIds.contains(it.id)
    }
  }

  fun achievementUnlockTimestamp(state: AppState, achievementId: String): String? {
    return state.achievements.unlockedTimestamps[achievementId]
  }
}
