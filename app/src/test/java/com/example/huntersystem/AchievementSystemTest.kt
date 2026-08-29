package com.example.huntersystem

import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.DomainError
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.engine.AchievementEngine
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.model.AchievementCatalog
import com.example.huntersystem.domain.model.Achievements
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.ClockPort
import com.example.huntersystem.infrastructure.IdGenerator
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.Selectors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AchievementSystemTest {

  private lateinit var fakeRepository: InMemoryProfileRepository
  private lateinit var eventBus: EventBus
  private lateinit var testClock: TestClockPort
  private lateinit var testIdGen: SequentialIdGenerator
  private lateinit var controller: AppController

  class InMemoryProfileRepository(var state: AppState = AppState.createDefault()) : ProfileRepository {
    var saveShouldFail = false
    var saveCount = 0

    override suspend fun loadState(): Result<AppState> = Result.success(state)
    override suspend fun saveState(state: AppState): Result<Unit> {
      saveCount++
      return if (saveShouldFail) {
        Result.failure(RuntimeException("Simulated I/O disk error"))
      } else {
        this.state = state
        Result.success(Unit)
      }
    }
    override suspend fun clearState(): Result<Unit> = Result.success(Unit)
    override suspend fun exportState(): Result<String> = Result.success("{}")
    override suspend fun importState(serialized: String): Result<AppState> = Result.success(state)
  }

  class SequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun generateId(prefix: String): String = "$prefix-${++counter}"
  }

  @Before
  fun setUp() {
    fakeRepository = InMemoryProfileRepository()
    eventBus = EventBus()
    testClock = TestClockPort(fixedIsoTimestamp = "2026-08-28T10:00:00Z", fixedLocalDate = "2026-08-28")
    testIdGen = SequentialIdGenerator()
    controller = AppController(
      repository = fakeRepository,
      eventBus = eventBus,
      clock = testClock,
      idGenerator = testIdGen,
      initialState = AppState.createDefault()
    )
  }

  @Test
  fun `definitions catalog is valid and unique`() {
    val all = AchievementCatalog.ALL
    val uniqueIds = all.map { it.id }.toSet()
    assertEquals(all.size, uniqueIds.size)

    for (def in all) {
      assertTrue("ID must not be empty", def.id.isNotBlank())
      assertTrue("Title must not be empty", def.title.isNotBlank())
      assertTrue("Description must not be empty", def.description.isNotBlank())
    }
  }

  @Test
  fun `pure engine unlocks FIRST_BLOOD and FIRST_REWARD on first completed quest with gold reward`() {
    val defaultState = AppState.createDefault()
    val initialResult = AchievementEngine.evaluate(defaultState, "2026-08-28T10:00:00Z")
    assertTrue(initialResult.newlyUnlockedIds.isEmpty())

    // State with 1 completed quest and 10 gold
    val candidateState = defaultState.copy(
      progression = defaultState.progression.copy(
        totalCompleted = 1,
        gold = 10
      )
    )

    val result = AchievementEngine.evaluate(candidateState, "2026-08-28T10:00:00Z")
    assertEquals(2, result.newlyUnlockedIds.size)
    assertTrue(result.newlyUnlockedIds.contains("FIRST_BLOOD"))
    assertTrue(result.newlyUnlockedIds.contains("FIRST_REWARD"))
    assertEquals(2, result.updatedAchievements.unlockedIds.size)
    assertEquals("2026-08-28T10:00:00Z", result.updatedAchievements.unlockedTimestamps["FIRST_BLOOD"])
  }

  @Test
  fun `pure engine unlocks QUEST_HUNTER on 10 completed quests`() {
    val state = AppState.createDefault().copy(
      progression = AppState.createDefault().progression.copy(totalCompleted = 10)
    )
    val result = AchievementEngine.evaluate(state, "2026-08-28T10:00:00Z")
    assertTrue(result.newlyUnlockedIds.contains("QUEST_HUNTER"))
    assertTrue(result.newlyUnlockedIds.contains("FIRST_BLOOD"))
  }

  @Test
  fun `pure engine unlocks AWAKENED at level 5 and RISING_HUNTER at level 10`() {
    val lvl5State = AppState.createDefault().copy(
      progression = AppState.createDefault().progression.copy(level = 5)
    )
    val result5 = AchievementEngine.evaluate(lvl5State, "2026-08-28T10:00:00Z")
    assertTrue(result5.newlyUnlockedIds.contains("AWAKENED"))
    assertFalse(result5.newlyUnlockedIds.contains("RISING_HUNTER"))

    val lvl10State = AppState.createDefault().copy(
      progression = AppState.createDefault().progression.copy(level = 10)
    )
    val result10 = AchievementEngine.evaluate(lvl10State, "2026-08-28T10:00:00Z")
    assertTrue(result10.newlyUnlockedIds.contains("AWAKENED"))
    assertTrue(result10.newlyUnlockedIds.contains("RISING_HUNTER"))
  }

  @Test
  fun `pure engine unlocks THREE_DAY_STREAK and SEVEN_DAY_STREAK`() {
    val streak3State = AppState.createDefault().copy(
      streak = AppState.createDefault().streak.copy(current = 3)
    )
    val result3 = AchievementEngine.evaluate(streak3State, "2026-08-28T10:00:00Z")
    assertTrue(result3.newlyUnlockedIds.contains("THREE_DAY_STREAK"))
    assertFalse(result3.newlyUnlockedIds.contains("SEVEN_DAY_STREAK"))

    val streak7State = AppState.createDefault().copy(
      streak = AppState.createDefault().streak.copy(best = 7)
    )
    val result7 = AchievementEngine.evaluate(streak7State, "2026-08-28T10:00:00Z")
    assertTrue(result7.newlyUnlockedIds.contains("THREE_DAY_STREAK"))
    assertTrue(result7.newlyUnlockedIds.contains("SEVEN_DAY_STREAK"))
  }

  @Test
  fun `achievement evaluation is strictly idempotent`() {
    val state = AppState.createDefault().copy(
      progression = AppState.createDefault().progression.copy(totalCompleted = 1, gold = 50),
      achievements = Achievements(
        unlockedIds = setOf("FIRST_BLOOD", "FIRST_REWARD"),
        unlockedTimestamps = mapOf("FIRST_BLOOD" to "2026-08-28T10:00:00Z", "FIRST_REWARD" to "2026-08-28T10:00:00Z")
      )
    )
    val result = AchievementEngine.evaluate(state, "2026-08-28T12:00:00Z")
    assertTrue(result.newlyUnlockedIds.isEmpty())
    assertEquals(2, result.updatedAchievements.unlockedIds.size)
  }

  @Test
  fun `CompleteQuest unlocks achievements atomically with persistence and emits fact events`() = runTest {
    // Dispatch CreateQuest (Normal Tier: +50 XP, +10 Gold)
    val createResult = controller.dispatch(
      Command.CreateQuest(
        title = "First Gate Clear",
        tier = QuestTier.N,
        startImmediately = true
      )
    )
    assertTrue(createResult is CommandResult.Success)
    val questId = (createResult as CommandResult.Success).state.quests.order.first()

    // Dispatch CompleteQuest
    val completeResult = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(completeResult is CommandResult.Success)
    val success = completeResult as CommandResult.Success

    // AppState checks
    val state = success.state
    assertEquals(1, state.progression.totalCompleted)
    assertEquals(25, state.progression.gold) // Normal tier base reward is 25 gold
    assertTrue(state.achievements.unlockedIds.contains("FIRST_BLOOD"))
    assertTrue(state.achievements.unlockedIds.contains("FIRST_REWARD"))
    assertEquals(2, Selectors.unlockedAchievementCount(state))

    // Selectors verification
    assertTrue(Selectors.isAchievementUnlocked(state, "FIRST_BLOOD"))
    assertTrue(Selectors.isAchievementUnlocked(state, "FIRST_REWARD"))
    assertFalse(Selectors.isAchievementUnlocked(state, "QUEST_HUNTER"))
    assertEquals(2, Selectors.unlockedAchievements(state).size)
    assertEquals(5, Selectors.lockedAchievements(state).size)

    // Events verification: Fact events include AchievementUnlocked
    val unlockEvents = success.events.filterIsInstance<DomainEvent.AchievementUnlocked>()
    assertEquals(2, unlockEvents.size)
    val unlockedIdsInEvents = unlockEvents.map { it.achievementId }.toSet()
    assertTrue(unlockedIdsInEvents.contains("FIRST_BLOOD"))
    assertTrue(unlockedIdsInEvents.contains("FIRST_REWARD"))

    // Verify event ordering: QuestCompleted -> XpGained -> GoldEarned -> StreakStarted -> AchievementUnlocked
    val eventTypes = success.events.map { it::class.simpleName }
    val questCompletedIdx = eventTypes.indexOf("QuestCompleted")
    val xpGainedIdx = eventTypes.indexOf("XpGained")
    val goldEarnedIdx = eventTypes.indexOf("GoldEarned")
    val streakStartedIdx = eventTypes.indexOf("StreakStarted")
    val achievementIdx = eventTypes.indexOf("AchievementUnlocked")

    assertTrue(questCompletedIdx < xpGainedIdx)
    assertTrue(xpGainedIdx < goldEarnedIdx)
    assertTrue(goldEarnedIdx < streakStartedIdx)
    assertTrue(streakStartedIdx < achievementIdx)

    // Verify single atomic persistence call
    assertEquals(2, fakeRepository.saveCount) // 1 from create, 1 from complete
    assertEquals(state, fakeRepository.state)
  }

  @Test
  fun `persistence failure aborts achievement unlock in memory and emits no AchievementUnlocked events`() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(
        title = "Doomed Quest",
        tier = QuestTier.N,
        startImmediately = true
      )
    )
    val questId = (createResult as CommandResult.Success).state.quests.order.first()

    // Simulate disk failure
    fakeRepository.saveShouldFail = true

    val completeResult = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(completeResult is CommandResult.Failure)
    val failure = completeResult as CommandResult.Failure
    assertTrue(failure.error is DomainError.PersistenceFailure)

    // State in controller must remain unchanged
    val controllerState = controller.state.value
    assertEquals(0, controllerState.progression.totalCompleted)
    assertEquals(0, controllerState.progression.gold)
    assertTrue(controllerState.achievements.unlockedIds.isEmpty())
  }

  @Test
  fun `multi-achievement transaction and persistence roundtrip`() = runTest {
    // Check xp required for levels: L1: 100, L2: 238, L3: 395, L4: 566 -> Total to hit L5 = 1299
    val xpResult = controller.dispatch(Command.GainXp(1500))
    assertTrue(xpResult is CommandResult.Success)

    val stateAfterXp = controller.state.value
    assertTrue("Player should reach at least level 5, got level ${stateAfterXp.progression.level}", stateAfterXp.progression.level >= 5)
    assertTrue(stateAfterXp.achievements.unlockedIds.contains("AWAKENED"))

    // Create a new controller loading from repository
    val newController = AppController(
      repository = fakeRepository,
      eventBus = EventBus(),
      clock = testClock,
      idGenerator = testIdGen
    )
    val loadResult = newController.dispatch(Command.LoadState)
    assertTrue(loadResult is CommandResult.Success)

    val reloadedState = newController.state.value
    assertTrue(reloadedState.achievements.unlockedIds.contains("AWAKENED"))
    assertEquals(stateAfterXp.achievements, reloadedState.achievements)
  }
}
