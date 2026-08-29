package com.example.huntersystem.domain.model

enum class QuestTier(
  val code: String,
  val label: String,
  val baseReward: QuestReward,
  val basePenalty: QuestPenalty
) {
  E("E", "E-Rank (Trivial)", QuestReward(xp = 20, gold = 10), QuestPenalty(xp = 5, gold = 0)),
  N("N", "N-Rank (Normal)", QuestReward(xp = 50, gold = 25), QuestPenalty(xp = 15, gold = 5)),
  H("H", "H-Rank (Hard)", QuestReward(xp = 100, gold = 50), QuestPenalty(xp = 30, gold = 15)),
  S("S", "S-Rank (Special)", QuestReward(xp = 250, gold = 150), QuestPenalty(xp = 75, gold = 50))
}

enum class QuestStatus(val value: String) {
  PENDING("pending"),
  ACTIVE("active"),
  COMPLETED("completed"),
  FAILED("failed"),
  CANCELLED("cancelled");

  val isTerminal: Boolean
    get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

data class QuestReward(
  val xp: Int,
  val gold: Int
)

data class QuestPenalty(
  val xp: Int,
  val gold: Int
)

data class Quest(
  val id: String,
  val title: String,
  val description: String = "",
  val tier: QuestTier,
  val status: QuestStatus,
  val createdAt: String,
  val updatedAt: String,
  val startedAt: String? = null,
  val completedAt: String? = null,
  val failedAt: String? = null,
  val cancelledAt: String? = null,
  val dueDate: String? = null,
  val notes: String = "",
  val reward: QuestReward = tier.baseReward,
  val penalty: QuestPenalty = tier.basePenalty
)
