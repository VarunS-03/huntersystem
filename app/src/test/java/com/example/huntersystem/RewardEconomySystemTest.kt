package com.example.huntersystem

import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.engine.RewardEngine
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.model.Progression
import com.example.huntersystem.domain.model.QuestPenalty
import com.example.huntersystem.domain.model.QuestReward
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.DeterministicIdGenerator
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.Selectors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RewardEconomySystemTest {

  private lateinit var repository: FakeProfileRepository
  private lateinit var eventBus: EventBus
  private lateinit var clock: TestClockPort
  private lateinit var idGenerator: DeterministicIdGenerator
  private lateinit var controller: AppController

  private class FakeProfileRepository(
    var storedState: AppState = AppState.createDefault(
      currentDate = "2026-08-28",
      currentTimestamp = "2026-08-28T10:00:00Z"
    ),
    var shouldFailSave: Boolean = false
  ) : ProfileRepository {
    override suspend fun loadState(): Result<AppState> = Result.success(storedState)
    override suspend fun saveState(state: AppState): Result<Unit> {
      if (shouldFailSave) return Result.failure(RuntimeException("Disk write failure"))
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
    repository = FakeProfileRepository()
    eventBus = EventBus()
    clock = TestClockPort(
      fixedTimeMillis = 1787824800000L,
      fixedIsoTimestamp = "2026-08-28T10:00:00Z",
      fixedLocalDate = "2026-08-28"
    )
    idGenerator = DeterministicIdGenerator()
    controller = AppController(
      repository = repository,
      eventBus = eventBus,
      clock = clock,
      idGenerator = idGenerator
    )
  }

  // 1. Pure RewardEngine Unit Tests
  @Test
  fun `applyReward increases gold and returns non-negative delta`() {
    val result = RewardEngine.applyReward(currentGold = 100, rewardGold = 50)
    assertEquals(150, result.updatedGold)
    assertEquals(50, result.delta)
  }

  @Test
  fun `applyReward with negative reward coerces to zero`() {
    val result = RewardEngine.applyReward(currentGold = 100, rewardGold = -20)
    assertEquals(100, result.updatedGold)
    assertEquals(0, result.delta)
  }

  @Test
  fun `applyPenalty deducts gold and returns negative delta`() {
    val result = RewardEngine.applyPenalty(currentGold = 100, penaltyGold = 30)
    assertEquals(70, result.updatedGold)
    assertEquals(-30, result.delta)
  }

  @Test
  fun `applyPenalty enforces gold non-negative invariant`() {
    val result = RewardEngine.applyPenalty(currentGold = 20, penaltyGold = 50)
    assertEquals(0, result.updatedGold)
    assertEquals(-20, result.delta) // only deducted available 20
  }

  @Test
  fun `applyDirectGain increases gold correctly`() {
    val result = RewardEngine.applyDirectGain(currentGold = 50, amount = 100)
    assertEquals(150, result.updatedGold)
    assertEquals(100, result.delta)
  }

  // 2. Selectors Tests
  @Test
  fun `economy selectors reflect state correctly`() {
    val state = AppState.createDefault().copy(
      progression = Progression(xp = 0, level = 1, gold = 250, rank = "E", totalCompleted = 0, totalFailed = 0)
    )
    assertEquals(250, Selectors.currentGold(state))
    assertTrue(Selectors.canAfford(state, 200))
    assertTrue(Selectors.canAfford(state, 250))
    assertFalse(Selectors.canAfford(state, 300))
    assertFalse(Selectors.canAfford(state, -10))
  }

  // 3. CompleteQuest Atomic Transaction Test
  @Test
  fun `CompleteQuest atomically updates Quest, XP, Level, Gold, Daily, Streak and emits GoldEarned event`() = runTest {
    controller.dispatch(Command.CreateProfile("Sung Jin-Woo"))
    val createResult = controller.dispatch(
      Command.CreateQuest(
        title = "Daily Training",
        tier = QuestTier.N, // N Tier: 50 XP, 25 Gold
        startImmediately = true
      )
    )
    val questId = (createResult as CommandResult.Success).state.quests.order.first()

    val completeResult = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(completeResult is CommandResult.Success)

    val state = (completeResult as CommandResult.Success).state

    // Quest state
    assertEquals("completed", state.quests.byId[questId]?.status?.value)

    // Progression & Economy
    assertEquals(50, state.progression.xp)
    assertEquals(25, state.progression.gold)
    assertEquals(1, state.progression.totalCompleted)

    // Daily
    assertEquals(1, state.daily.completedCount)
    assertEquals(50, state.daily.earnedXp)
    assertEquals(25, state.daily.earnedGold)

    // Streak
    assertEquals(1, state.streak.current)

    // Events
    val goldEvents = (completeResult as CommandResult.Success).events.filterIsInstance<DomainEvent.GoldEarned>()
    assertEquals(1, goldEvents.size)
    assertEquals(25, goldEvents[0].amount)
    assertEquals(25, goldEvents[0].resultingBalance)
    assertEquals("quest_completion", goldEvents[0].source)
  }

  // 4. Exactly-Once Reward Invariant
  @Test
  fun `Completing a quest repeatedly does not grant duplicate gold`() = runTest {
    controller.dispatch(Command.CreateProfile("Hunter"))
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "One-Time Dungeon", tier = QuestTier.H, startImmediately = true) // 50 Gold
    )
    val questId = (createResult as CommandResult.Success).state.quests.order.first()

    val firstComplete = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(firstComplete is CommandResult.Success)
    assertEquals(50, (firstComplete as CommandResult.Success).state.progression.gold)

    // Second completion must fail
    val secondComplete = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(secondComplete is CommandResult.Failure)

    // Controller state unchanged
    val finalState = controller.state.value
    assertEquals(50, finalState.progression.gold)
    assertEquals(50, finalState.daily.earnedGold)
    assertEquals(1, finalState.daily.completedCount)
  }

  // 5. FailQuest Penalty Test
  @Test
  fun `FailQuest applies penalty gold and enforces non-negative invariant`() = runTest {
    controller.dispatch(Command.CreateProfile("Hunter"))
    // Grant 10 gold first
    controller.dispatch(Command.GainGold(10))
    assertEquals(10, controller.state.value.progression.gold)

    // Create Hard Quest (Penalty: 15 Gold)
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Raid Boss", tier = QuestTier.H, startImmediately = true)
    )
    val questId = (createResult as CommandResult.Success).state.quests.order.first()

    val failResult = controller.dispatch(Command.FailQuest(questId))
    assertTrue(failResult is CommandResult.Success)

    val state = (failResult as CommandResult.Success).state
    // Gold was 10, penalty was 15 -> Gold becomes 0 (non-negative invariant)
    assertEquals(0, state.progression.gold)
    assertEquals(1, state.progression.totalFailed)
    assertEquals(1, state.daily.failedCount)

    val penaltyEvents = (failResult as CommandResult.Success).events.filterIsInstance<DomainEvent.GoldPenaltyApplied>()
    assertEquals(1, penaltyEvents.size)
    assertEquals(10, penaltyEvents[0].amount) // actual deducted amount
    assertEquals(0, penaltyEvents[0].resultingBalance)
    assertEquals("quest_failure", penaltyEvents[0].source)
  }

  // 6. Direct GainGold Command Test
  @Test
  fun `GainGold command validates input and updates balance`() = runTest {
    controller.dispatch(Command.CreateProfile("Hunter"))
    
    // Invalid amount
    val failResult = controller.dispatch(Command.GainGold(0))
    assertTrue(failResult is CommandResult.Failure)

    // Valid amount
    val successResult = controller.dispatch(Command.GainGold(200))
    assertTrue(successResult is CommandResult.Success)
    val state = (successResult as CommandResult.Success).state
    assertEquals(200, state.progression.gold)

    val events = (successResult as CommandResult.Success).events.filterIsInstance<DomainEvent.GoldEarned>()
    assertEquals(1, events.size)
    assertEquals(200, events[0].amount)
    assertEquals(200, events[0].resultingBalance)
    assertEquals("manual_grant", events[0].source)
  }

  // 7. Atomic Persistence Failure Safety
  @Test
  fun `Persistence failure prevents in-memory state mutation and event emission`() = runTest {
    controller.dispatch(Command.CreateProfile("Hunter"))
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Dungeon", tier = QuestTier.S, startImmediately = true) // 150 Gold
    )
    val questId = (createResult as CommandResult.Success).state.quests.order.first()

    // Simulate disk failure
    repository.shouldFailSave = true

    val completeResult = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(completeResult is CommandResult.Failure)

    // In-memory state remains untouched
    val state = controller.state.value
    assertEquals(0, state.progression.gold)
    assertEquals(0, state.progression.xp)
    assertEquals("active", state.quests.byId[questId]?.status?.value)
    assertEquals(0, state.daily.completedCount)
    assertEquals(0, state.daily.earnedGold)
  }

  // 8. Multi-Day Rollover with Gold Accumulation
  @Test
  fun `Daily earned gold resets on day rollover while total wallet gold persists`() = runTest {
    clock.setTime(1787824800000L, "2026-08-28T10:00:00Z", "2026-08-28")
    controller.dispatch(Command.CreateProfile("Hunter"))
    
    // Day 1
    val q1 = (controller.dispatch(Command.CreateQuest("Day 1 Quest", tier = QuestTier.N, startImmediately = true)) as CommandResult.Success).state.quests.order.first()
    controller.dispatch(Command.CompleteQuest(q1))

    val day1State = controller.state.value
    assertEquals(25, day1State.progression.gold)
    assertEquals(25, day1State.daily.earnedGold)

    // Advance to Day 2 (2026-08-29)
    clock.setTime(1787911200000L, "2026-08-29T10:00:00Z", "2026-08-29")
    controller.dispatch(Command.ResolveDailyCycle)

    val rolloverState = controller.state.value
    assertEquals(25, rolloverState.progression.gold) // Wallet preserved
    assertEquals(0, rolloverState.daily.earnedGold) // Daily counter reset

    // Day 2 Quest completion
    val q2 = (controller.dispatch(Command.CreateQuest("Day 2 Quest", tier = QuestTier.H, startImmediately = true)) as CommandResult.Success).state.quests.order.last()
    val completeQ2Result = controller.dispatch(Command.CompleteQuest(q2))
    assertTrue(completeQ2Result is CommandResult.Success)

    val day2State = controller.state.value
    assertEquals(75, day2State.progression.gold) // 25 + 50
    assertEquals(50, day2State.daily.earnedGold) // 50 earned today
  }
}
