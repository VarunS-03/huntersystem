package com.example.huntersystem.domain.model

data class AchievementDefinition(
  val id: String,
  val title: String,
  val description: String,
  val category: AchievementCategory
)

enum class AchievementCategory {
  QUEST,
  PROGRESSION,
  STREAK,
  ECONOMY
}

object AchievementCatalog {
  val FIRST_BLOOD = AchievementDefinition(
    id = "FIRST_BLOOD",
    title = "First Blood",
    description = "Complete your first quest.",
    category = AchievementCategory.QUEST
  )

  val QUEST_HUNTER = AchievementDefinition(
    id = "QUEST_HUNTER",
    title = "Quest Hunter",
    description = "Complete 10 quests.",
    category = AchievementCategory.QUEST
  )

  val AWAKENED = AchievementDefinition(
    id = "AWAKENED",
    title = "Awakened",
    description = "Reach level 5.",
    category = AchievementCategory.PROGRESSION
  )

  val RISING_HUNTER = AchievementDefinition(
    id = "RISING_HUNTER",
    title = "Rising Hunter",
    description = "Reach level 10.",
    category = AchievementCategory.PROGRESSION
  )

  val THREE_DAY_STREAK = AchievementDefinition(
    id = "THREE_DAY_STREAK",
    title = "Persistent Will",
    description = "Maintain a 3-day streak.",
    category = AchievementCategory.STREAK
  )

  val SEVEN_DAY_STREAK = AchievementDefinition(
    id = "SEVEN_DAY_STREAK",
    title = "Iron Discipline",
    description = "Maintain a 7-day streak.",
    category = AchievementCategory.STREAK
  )

  val FIRST_REWARD = AchievementDefinition(
    id = "FIRST_REWARD",
    title = "First Reward",
    description = "Earn gold from a quest.",
    category = AchievementCategory.ECONOMY
  )

  val ALL: List<AchievementDefinition> = listOf(
    FIRST_BLOOD,
    QUEST_HUNTER,
    AWAKENED,
    RISING_HUNTER,
    THREE_DAY_STREAK,
    SEVEN_DAY_STREAK,
    FIRST_REWARD
  )

  val BY_ID: Map<String, AchievementDefinition> = ALL.associateBy { it.id }
}
