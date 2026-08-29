package com.example.huntersystem

import com.example.huntersystem.domain.model.Quest
import com.example.huntersystem.domain.model.QuestPenalty
import com.example.huntersystem.domain.model.QuestReward
import com.example.huntersystem.domain.model.QuestStatus
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.QuestsState
import com.example.huntersystem.state.Selectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {

  @Test
  fun testCreateDefaultState() {
    val state = AppState.createDefault(
      profileId = "test_hunter",
      displayName = "Sung Jinwoo",
      currentDate = "2026-08-28",
      currentTimestamp = "2026-08-28T10:00:00Z"
    )

    assertEquals("test_hunter", state.profile.id)
    assertEquals("Sung Jinwoo", state.profile.displayName)
    assertEquals(1, state.progression.level)
    assertEquals(0, state.progression.xp)
    assertEquals("E", state.progression.rank)
    assertEquals(0, state.streak.current)
    assertEquals("2026-08-28", state.daily.activeDate)
    assertEquals(1L, state.metadata.revision)
    assertTrue(state.quests.byId.isEmpty())
  }

  @Test
  fun testImmutabilityViaCopy() {
    val initial = AppState.createDefault()
    val updated = initial.copy(
      progression = initial.progression.copy(level = 2, xp = 150)
    )

    assertEquals(1, initial.progression.level)
    assertEquals(0, initial.progression.xp)
    assertEquals(2, updated.progression.level)
    assertEquals(150, updated.progression.xp)
  }

  @Test
  fun testSelectorsCalculations() {
    val reqLevel1 = Selectors.xpRequiredForNextLevel(1)
    assertEquals(100, reqLevel1)

    val progress = Selectors.xpProgressPercent(50, 1)
    assertEquals(0.5f, progress, 0.001f)

    val quest = Quest(
      id = "q1",
      title = "100 Pushups",
      tier = QuestTier.N,
      status = QuestStatus.ACTIVE,
      createdAt = "2026-08-28T10:00:00Z",
      updatedAt = "2026-08-28T10:00:00Z",
      dueDate = "2026-08-28",
      reward = QuestReward(50, 20),
      penalty = QuestPenalty(25, 10)
    )

    val state = AppState.createDefault().copy(
      quests = QuestsState(
        byId = mapOf("q1" to quest),
        order = listOf("q1")
      )
    )

    val active = Selectors.activeQuests(state)
    assertEquals(1, active.size)
    assertEquals("q1", active[0].id)
    assertFalse(Selectors.isQuestOverdue(quest, "2026-08-28"))
    assertTrue(Selectors.isQuestOverdue(quest, "2026-08-29"))
  }
}
