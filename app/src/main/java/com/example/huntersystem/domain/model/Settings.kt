package com.example.huntersystem.domain.model

data class Settings(
  val reducedMotion: Boolean = false,
  val soundEnabled: Boolean = true,
  val theme: String = "SYSTEM_DARK",
  val defaultQuestTier: QuestTier = QuestTier.N
)
