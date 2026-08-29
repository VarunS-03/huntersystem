package com.example.huntersystem.application

import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.domain.model.Settings

sealed interface Command {
  data object LoadState : Command
  data class ResetProfile(val displayName: String = "") : Command
  data class CreateProfile(val displayName: String) : Command
  data class UpdateDisplayName(val displayName: String) : Command
  data class UpdateSettings(val settings: Settings) : Command
  data object ResolveDailyCycle : Command

  // Progression domain commands
  data class GainXp(val amount: Int) : Command

  // Economy domain commands
  data class GainGold(val amount: Int) : Command

  // Quest domain commands
  data class CreateQuest(
    val title: String,
    val description: String = "",
    val tier: QuestTier = QuestTier.N,
    val dueDate: String? = null,
    val startImmediately: Boolean = true
  ) : Command

  data class StartQuest(val questId: String) : Command
  data class CompleteQuest(val questId: String) : Command
  data class FailQuest(val questId: String) : Command
  data class CancelQuest(val questId: String) : Command
  data class AbandonQuest(val questId: String) : Command
  data class DeleteQuest(val questId: String) : Command
}
