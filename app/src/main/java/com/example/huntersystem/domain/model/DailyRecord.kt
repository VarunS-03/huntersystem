package com.example.huntersystem.domain.model

data class DailyRecord(
  val activeDate: String,
  val loginDates: List<String>,
  val completedCount: Int,
  val failedCount: Int,
  val earnedXp: Int,
  val earnedGold: Int
)
