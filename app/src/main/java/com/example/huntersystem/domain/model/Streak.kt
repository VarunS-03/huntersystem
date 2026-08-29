package com.example.huntersystem.domain.model

data class Streak(
  val current: Int,
  val best: Int,
  val lastActivityDate: String?,
  val shieldCount: Int
)
