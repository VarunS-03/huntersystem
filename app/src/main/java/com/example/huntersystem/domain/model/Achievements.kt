package com.example.huntersystem.domain.model

data class Achievements(
  val unlockedIds: Set<String> = emptySet(),
  val unlockedTimestamps: Map<String, String> = emptyMap()
)
