package com.example.huntersystem

import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.DomainError
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.engine.ProgressionEngine
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.model.Progression
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.DeterministicIdGenerator
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.Selectors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProgressionSystemTest {

  private lateinit var fakeRepository: FakeProfileRepository
  private lateinit var eventBus: EventBus
  private lateinit var clock: TestClockPort
  private lateinit var idGenerator: DeterministicIdGenerator
  private lateinit var controller: AppController

  class FakeProfileRepository : ProfileRepository {
    var storedState: AppState = AppState.createDefault()

    override suspend fun loadState(): Result<AppState> = Result.success(storedState)
    override suspend fun saveState(state: AppState): Result<Unit> {
      storedState = state
      return Result.success(Unit)
    }
    override suspend fun clearState(): Result<Unit> {
      storedState = AppState.createDefault()
      return Result.success(Unit)
    }
    override suspend fun exportState(): Result<String> = Result.success("")
    override suspend fun importState(serialized: String): Result<AppState> = Result.success(storedState)
  }

  @Before
  fun setUp() {
    fakeRepository = FakeProfileRepository()
    eventBus = EventBus()
    clock = TestClockPort(
      fixedTimeMillis = 1787997600000L,
      fixedIsoTimestamp = "2026-08-29T10:00:00Z",
      fixedLocalDate = "2026-08-29"
    )
    idGenerator = DeterministicIdGenerator()
    controller = AppController(
      repository = fakeRepository,
      eventBus = eventBus,
      clock = clock,
      idGenerator = idGenerator,
      initialState = AppState.createDefault()
    )
  }

  @Test
  fun testXpFormulaThresholds() {
    // Level 1: 100 XP
    assertEquals(100, ProgressionEngine.xpRequiredForLevel(1))
    // Level 2: (100 * 2^1.25) = round(100 * 2.3784) = 238 XP
    assertEquals(238, ProgressionEngine.xpRequiredForLevel(2))
    // Level 3: (100 * 3^1.25) = round(100 * 3.9482) = 395 XP
    assertEquals(395, ProgressionEngine.xpRequiredForLevel(3))
  }

  @Test
  fun testRankThresholds() {
    assertEquals("E", ProgressionEngine.rankForLevel(1))
    assertEquals("E", ProgressionEngine.rankForLevel(4))
    assertEquals("D", ProgressionEngine.rankForLevel(5))
    assertEquals("D", ProgressionEngine.rankForLevel(9))
    assertEquals("C", ProgressionEngine.rankForLevel(10))
    assertEquals("B", ProgressionEngine.rankForLevel(20))
    assertEquals("A", ProgressionEngine.rankForLevel(30))
    assertEquals("S", ProgressionEngine.rankForLevel(50))
    assertEquals("S", ProgressionEngine.rankForLevel(100))
  }

  @Test
  fun testAccumulateXpWithoutLevelUp() = runTest {
    val initial = Progression(xp = 0, level = 1, gold = 0, rank = "E", totalCompleted = 0, totalFailed = 0)
    val result = ProgressionEngine.applyXpGain(initial, 60)

    assertEquals(60, result.updatedProgression.xp)
    assertEquals(1, result.updatedProgression.level)
    assertEquals("E", result.updatedProgression.rank)
    assertEquals(0, result.levelsGained)
    assertFalse(result.rankPromoted)
  }

  @Test
  fun testExactLevelUp() = runTest {
    val initial = Progression(xp = 0, level = 1, gold = 0, rank = "E", totalCompleted = 0, totalFailed = 0)
    val result = ProgressionEngine.applyXpGain(initial, 100)

    // 100 XP consumed -> Level 2, 0 leftover XP
    assertEquals(0, result.updatedProgression.xp)
    assertEquals(2, result.updatedProgression.level)
    assertEquals("E", result.updatedProgression.rank)
    assertEquals(1, result.levelsGained)
    assertEquals(1, result.oldLevel)
    assertEquals(2, result.newLevel)
  }

  @Test
  fun testLevelUpWithRemainderXp() = runTest {
    val initial = Progression(xp = 20, level = 1, gold = 0, rank = "E", totalCompleted = 0, totalFailed = 0)
    val result = ProgressionEngine.applyXpGain(initial, 110)

    // 20 + 110 = 130 XP. Level 1 needs 100. Leftover = 30.
    assertEquals(30, result.updatedProgression.xp)
    assertEquals(2, result.updatedProgression.level)
    assertEquals(1, result.levelsGained)
  }

  @Test
  fun testMultiLevelUpAndRankPromotion() = runTest {
    // Starting at Level 4 (E-rank), with 0 XP
    // Level 4 requires: 100 * 4^1.25 = 100 * 5.6568 = 566 XP
    // Level 5 (D-rank) requires: 100 * 5^1.25 = 100 * 7.4767 = 748 XP
    // Giving 566 + 748 + 50 = 1364 XP should advance 4 -> 5 -> 6 (gaining 2 levels and promoting to D rank)
    val initial = Progression(xp = 0, level = 4, gold = 0, rank = "E", totalCompleted = 0, totalFailed = 0)
    val result = ProgressionEngine.applyXpGain(initial, 1364)

    assertEquals(6, result.updatedProgression.level)
    assertEquals(50, result.updatedProgression.xp)
    assertEquals("D", result.updatedProgression.rank)
    assertEquals(2, result.levelsGained)
    assertTrue(result.rankPromoted)
    assertEquals("E", result.oldRank)
    assertEquals("D", result.newRank)
  }

  @Test
  fun testGainXpCommandPipeline() = runTest {
    val result = controller.dispatch(Command.GainXp(150))
    assertTrue(result is CommandResult.Success)
    val success = result as CommandResult.Success

    // 150 XP from Level 1 (needs 100) -> Level 2 with 50 XP
    assertEquals(2, success.state.progression.level)
    assertEquals(50, success.state.progression.xp)

    val xpEvent = success.events.first { it is DomainEvent.XpGained } as DomainEvent.XpGained
    assertEquals(150, xpEvent.amount)
    assertEquals(50, xpEvent.totalXp)
    assertEquals(2, xpEvent.currentLevel)

    val levelUpEvent = success.events.first { it is DomainEvent.LevelUp } as DomainEvent.LevelUp
    assertEquals(1, levelUpEvent.oldLevel)
    assertEquals(2, levelUpEvent.newLevel)
  }

  @Test
  fun testGainXpValidationFailure_NegativeOrZero() = runTest {
    val resultZero = controller.dispatch(Command.GainXp(0))
    assertTrue(resultZero is CommandResult.Failure)
    assertTrue((resultZero as CommandResult.Failure).error is DomainError.InvalidInput)

    val resultNeg = controller.dispatch(Command.GainXp(-50))
    assertTrue(resultNeg is CommandResult.Failure)
    assertTrue((resultNeg as CommandResult.Failure).error is DomainError.InvalidInput)
  }

  @Test
  fun testQuestCompletionAwardsTierXp() = runTest {
    // 1. Create S-Rank Quest (gives 250 XP)
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "S-Rank Dungeon Clearance", tier = QuestTier.S)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    // 2. Complete Quest
    val completeResult = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(completeResult is CommandResult.Success)
    val success = completeResult as CommandResult.Success

    // Level 1 needs 100 XP -> Level 2 needs 238 XP.
    // 250 XP - 100 = 150 leftover on Level 2
    assertEquals(2, success.state.progression.level)
    assertEquals(150, success.state.progression.xp)
    assertEquals(1, success.state.progression.totalCompleted)

    // Events verified
    assertTrue(success.events.any { it is DomainEvent.QuestCompleted })
    assertTrue(success.events.any { it is DomainEvent.XpGained })
    assertTrue(success.events.any { it is DomainEvent.LevelUp })
  }

  @Test
  fun testQuestFailureUpdatesTotalFailed() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Impossible Dungeon", tier = QuestTier.S)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    val failResult = controller.dispatch(Command.FailQuest(questId))
    assertTrue(failResult is CommandResult.Success)
    val success = failResult as CommandResult.Success

    assertEquals(1, success.state.progression.totalFailed)
    assertEquals(0, success.state.progression.xp)
    assertEquals(1, success.state.progression.level)
  }

  @Test
  fun testNoDoubleAwardOnRepeatedCompletion() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Solo Dungeon", tier = QuestTier.N)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    // 1st completion: +50 XP
    val complete1 = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(complete1 is CommandResult.Success)
    val state1 = (complete1 as CommandResult.Success).state
    assertEquals(50, state1.progression.xp)

    // 2nd completion attempt: must fail and NOT award XP again
    val complete2 = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(complete2 is CommandResult.Failure)
    val state2 = controller.state.value
    assertEquals(50, state2.progression.xp)
  }

  @Test
  fun testStateIsolationDuringXpGain() = runTest {
    val initialStreak = controller.state.value.streak
    val initialDaily = controller.state.value.daily
    val initialProfile = controller.state.value.profile
    val initialSettings = controller.state.value.settings
    val initialAchievements = controller.state.value.achievements

    val result = controller.dispatch(Command.GainXp(75))
    assertTrue(result is CommandResult.Success)
    val postState = (result as CommandResult.Success).state

    // Progression changed
    assertEquals(75, postState.progression.xp)

    // Unrelated states are strictly untouched
    assertEquals(initialStreak, postState.streak)
    assertEquals(initialDaily, postState.daily)
    assertEquals(initialProfile, postState.profile)
    assertEquals(initialSettings, postState.settings)
    assertEquals(initialAchievements, postState.achievements)
  }

  @Test
  fun testPersistenceRoundTripWithProgression() = runTest {
    // 1. Gain XP & level up to Level 2
    controller.dispatch(Command.GainXp(120))
    val stateBefore = controller.state.value
    assertEquals(2, stateBefore.progression.level)
    assertEquals(20, stateBefore.progression.xp)

    // 2. Load directly from repository
    val loadedStateResult = fakeRepository.loadState()
    assertTrue(loadedStateResult.isSuccess)
    val loadedState = loadedStateResult.getOrThrow()

    assertEquals(2, loadedState.progression.level)
    assertEquals(20, loadedState.progression.xp)
    assertEquals("E", loadedState.progression.rank)
  }

  @Test
  fun testSelectorsDerivedValues() {
    // Level 1 requires 100 XP
    assertEquals(100, Selectors.xpRequiredForNextLevel(1))
    assertEquals(0.5f, Selectors.xpProgressPercent(50, 1), 0.001f)
    assertEquals(1.0f, Selectors.xpProgressPercent(100, 1), 0.001f)
    assertEquals(0.0f, Selectors.xpProgressPercent(0, 1), 0.001f)

    // Rank selector
    assertEquals("E", Selectors.currentRank(1))
    assertEquals("D", Selectors.currentRank(5))
    assertEquals("C", Selectors.currentRank(10))
    assertEquals("B", Selectors.currentRank(20))
    assertEquals("A", Selectors.currentRank(30))
    assertEquals("S", Selectors.currentRank(50))
  }
}
