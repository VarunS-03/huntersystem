package com.example.huntersystem.domain.model

data class Progression(
  val xp: Int,
  val level: Int,
  val gold: Int,
  val rank: String,
  val totalCompleted: Int,
  val totalFailed: Int
)
