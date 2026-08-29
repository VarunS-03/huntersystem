package com.example.huntersystem

import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.engine.DailyEngine
import com.example.huntersystem.domain.engine.StreakEngine
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.model.DailyRecord
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.domain.model.Streak
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.DeterministicIdGenerator
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.Selectors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DailyStreakSystemTest {

  private lateinit var fakeRepository: FakeProfileRepository
  private lateinit var eventBus: EventBus
  private lateinit var clock: TestClockPort
  private lateinit var idGenerator: DeterministicIdGenerator
  private lateinit var controller: AppController

  class FakeProfileRepository : ProfileRepository {
    var storedState: AppState = AppState.createDefault(
      currentDate = "2026-08-28",
      currentTimestamp = "2026-08-28T10:00:00Z"
    )

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
      fixedTimeMillis = 1787824800000L,
      fixedIsoTimestamp = "2026-08-28T10:00:00Z",
      fixedLocalDate = "2026-08-28"
    )
    idGenerator = DeterministicIdGenerator()
    controller = AppController(
      repository = fakeRepository,
      eventBus = eventBus,
      clock = clock,
      idGenerator = idGenerator,
      initialState = fakeRepository.storedState
    )
  }

  // ==========================================
  // DailyEngine Unit Tests
  // ==========================================

  @Test
  fun testDailySameDayResolution_NoRollover() {
    val daily = DailyRecord(
      activeDate = "2026-08-28",
      loginDates = listOf("2026-08-28"),
      completedCount = 2,
      failedCount = 1,
      earnedXp = 100,
      earnedGold = 20
    )
    val res = DailyEngine.resolveDay(daily, "2026-08-28")
    assertFalse(res.isRollover)
    assertEquals(daily, res.updatedDaily)
  }

  @Test
  fun testDailyNewDayResolution_RolloverResetsCounters() {
    val daily = DailyRecord(
      activeDate = "2026-08-28",
      loginDates = listOf("2026-08-28"),
      completedCount = 3,
      failedCount = 0,
      earnedXp = 150,
      earnedGold = 50
    )
    val res = DailyEngine.resolveDay(daily, "2026-08-29")
    assertTrue(res.isRollover)
    assertEquals("2026-08-29", res.updatedDaily.activeDate)
    assertEquals(listOf("2026-08-28", "2026-08-29"), res.updatedDaily.loginDates)
    assertEquals(0, res.updatedDaily.completedCount)
    assertEquals(0, res.updatedDaily.failedCount)
    assertEquals(0, res.updatedDaily.earnedXp)
    assertEquals(0, res.updatedDaily.earnedGold)
  }

  @Test
  fun testDailyQuestCompletionAndFailureCounters() {
    val daily = DailyRecord(
      activeDate = "2026-08-28",
      loginDates = listOf("2026-08-28"),
      completedCount = 0,
      failedCount = 0,
      earnedXp = 0,
      earnedGold = 0
    )
    val afterComplete = DailyEngine.recordQuestCompletion(daily, "2026-08-28", xpGained = 50, goldGained = 10)
    assertEquals(1, afterComplete.completedCount)
    assertEquals(50, afterComplete.earnedXp)
    assertEquals(10, afterComplete.earnedGold)

    val afterFail = DailyEngine.recordQuestFailure(afterComplete, "2026-08-28")
    assertEquals(1, afterFail.completedCount)
    assertEquals(1, afterFail.failedCount)
  }

  // ==========================================
  // StreakEngine Unit Tests
  // ==========================================

  @Test
  fun testStreakFirstQualifyingDay() {
    val initial = Streak(current = 0, best = 0, lastActivityDate = null, shieldCount = 0)
    val result = StreakEngine.recordQualifyingActivity(initial, "2026-08-28")

    assertTrue(result.isStarted)
    assertFalse(result.isContinued)
    assertTrue(result.isIncremented)
    assertTrue(result.isBestNew)
    assertEquals(1, result.updatedStreak.current)
    assertEquals(1, result.updatedStreak.best)
    assertEquals("2026-08-28", result.updatedStreak.lastActivityDate)
  }

  @Test
  fun testStreakSameDayIdempotency() {
    val initial = Streak(current = 1, best = 1, lastActivityDate = "2026-08-28", shieldCount = 0)
    val result = StreakEngine.recordQualifyingActivity(initial, "2026-08-28")

    assertFalse(result.isStarted)
    assertFalse(result.isContinued)
    assertFalse(result.isIncremented)
    assertFalse(result.isBestNew)
    assertEquals(1, result.updatedStreak.current)
    assertEquals(1, result.updatedStreak.best)
  }

  @Test
  fun testStreakConsecutiveDayContinuation() {
    val initial = Streak(current = 1, best = 1, lastActivityDate = "2026-08-28", shieldCount = 0)
    val result = StreakEngine.recordQualifyingActivity(initial, "2026-08-29")

    assertFalse(result.isStarted)
    assertTrue(result.isContinued)
    assertTrue(result.isIncremented)
    assertTrue(result.isBestNew)
    assertEquals(2, result.updatedStreak.current)
    assertEquals(2, result.updatedStreak.best)
    assertEquals("2026-08-29", result.updatedStreak.lastActivityDate)
  }

  @Test
  fun testStreakBrokenOnMissedDayRollover() {
    // Last active on 2026-08-28, today is 2026-08-30 (gap of 2 days)
    val initial = Streak(current = 3, best = 5, lastActivityDate = "2026-08-28", shieldCount = 0)
    val rollover = StreakEngine.evaluateStreakOnRollover(initial, "2026-08-30")

    assertTrue(rollover.isBroken)
    assertEquals(3, rollover.previousStreak)
    assertEquals(0, rollover.updatedStreak.current)
    assertEquals(5, rollover.updatedStreak.best) // best is preserved
  }

  @Test
  fun testStreakRestartAfterBreak() {
    // Streak was broken (current = 0, best = 5, lastActivityDate = "2026-08-28")
    val brokenStreak = Streak(current = 0, best = 5, lastActivityDate = "2026-08-28", shieldCount = 0)
    val result = StreakEngine.recordQualifyingActivity(brokenStreak, "2026-08-31")

    assertTrue(result.isStarted)
    assertFalse(result.isContinued)
    assertTrue(result.isIncremented)
    assertFalse(result.isBestNew) // 1 is not higher than best 5
    assertEquals(1, result.updatedStreak.current)
    assertEquals(5, result.updatedStreak.best)
    assertEquals("2026-08-31", result.updatedStreak.lastActivityDate)
  }

  // ==========================================
  // End-to-End Simulation & Integration Tests
  // ==========================================

  @Test
  fun testMultiDaySimulation_Day1_Day2_Day3_Day5() = runTest {
    // ==========================================
    // Day 1: 2026-08-28
    // ==========================================
    clock.setTime(1787824800000L, "2026-08-28T10:00:00Z", "2026-08-28")

    // Create & complete quest on Day 1
    val q1Result = controller.dispatch(Command.CreateQuest(title = "Day 1 Quest")) as CommandResult.Success
    val q1Id = q1Result.state.quests.order.first()

    val completeQ1 = controller.dispatch(Command.CompleteQuest(q1Id))
    assertTrue(completeQ1 is CommandResult.Success)
    val stateDay1 = (completeQ1 as CommandResult.Success).state

    assertEquals(1, stateDay1.daily.completedCount)
    assertEquals("2026-08-28", stateDay1.daily.activeDate)
    assertEquals(1, stateDay1.streak.current)
    assertEquals(1, stateDay1.streak.best)
    assertEquals("2026-08-28", stateDay1.streak.lastActivityDate)
    assertTrue(completeQ1.events.any { it is DomainEvent.StreakStarted })

    // Second quest on Day 1 (Idempotency test)
    val q1bResult = controller.dispatch(Command.CreateQuest(title = "Day 1 Quest 2")) as CommandResult.Success
    val q1bId = q1bResult.state.quests.order.last()
    val completeQ1b = controller.dispatch(Command.CompleteQuest(q1bId))
    assertTrue(completeQ1b is CommandResult.Success)
    val stateDay1b = (completeQ1b as CommandResult.Success).state

    assertEquals(2, stateDay1b.daily.completedCount)
    assertEquals(1, stateDay1b.streak.current) // Unchanged!
    assertEquals(1, stateDay1b.streak.best)

    // ==========================================
    // Day 2: 2026-08-29 (Consecutive Day)
    // ==========================================
    clock.setTime(1787911200000L, "2026-08-29T10:00:00Z", "2026-08-29")

    // Daily rollover occurs on next command
    val q2Result = controller.dispatch(Command.CreateQuest(title = "Day 2 Quest")) as CommandResult.Success
    val q2Id = q2Result.state.quests.order.last()

    val completeQ2 = controller.dispatch(Command.CompleteQuest(q2Id))
    assertTrue(completeQ2 is CommandResult.Success)
    val stateDay2 = (completeQ2 as CommandResult.Success).state

    assertEquals(1, stateDay2.daily.completedCount) // reset for day 2 + 1 completion
    assertEquals("2026-08-29", stateDay2.daily.activeDate)
    assertEquals(2, stateDay2.streak.current) // Streak continued!
    assertEquals(2, stateDay2.streak.best)
    assertEquals("2026-08-29", stateDay2.streak.lastActivityDate)
    assertTrue(completeQ2.events.any { it is DomainEvent.StreakContinued })

    // ==========================================
    // Day 3: 2026-08-30 (Consecutive Day)
    // ==========================================
    clock.setTime(1787997600000L, "2026-08-30T10:00:00Z", "2026-08-30")

    val q3Result = controller.dispatch(Command.CreateQuest(title = "Day 3 Quest")) as CommandResult.Success
    val q3Id = q3Result.state.quests.order.last()

    val completeQ3 = controller.dispatch(Command.CompleteQuest(q3Id))
    assertTrue(completeQ3 is CommandResult.Success)
    val stateDay3 = (completeQ3 as CommandResult.Success).state

    assertEquals(1, stateDay3.daily.completedCount)
    assertEquals(3, stateDay3.streak.current) // Streak = 3!
    assertEquals(3, stateDay3.streak.best)
    assertEquals("2026-08-30", stateDay3.streak.lastActivityDate)

    // ==========================================
    // Day 5: 2026-09-01 (Missed Day 4 on 2026-08-31)
    // ==========================================
    clock.setTime(1788170400000L, "2026-09-01T10:00:00Z", "2026-09-01")

    // Resolve daily cycle detects streak break
    val resolveCycle = controller.dispatch(Command.ResolveDailyCycle)
    assertTrue(resolveCycle is CommandResult.Success)
    val stateDay5Initial = (resolveCycle as CommandResult.Success).state

    assertEquals("2026-09-01", stateDay5Initial.daily.activeDate)
    assertEquals(0, stateDay5Initial.daily.completedCount)
    assertEquals(0, stateDay5Initial.streak.current) // Broken!
    assertEquals(3, stateDay5Initial.streak.best) // Best preserved!
    assertTrue(resolveCycle.events.any { it is DomainEvent.StreakBroken })

    // Complete quest on Day 5 to restart streak
    val q5Result = controller.dispatch(Command.CreateQuest(title = "Day 5 Quest")) as CommandResult.Success
    val q5Id = q5Result.state.quests.order.last()

    val completeQ5 = controller.dispatch(Command.CompleteQuest(q5Id))
    assertTrue(completeQ5 is CommandResult.Success)
    val stateDay5Final = (completeQ5 as CommandResult.Success).state

    assertEquals(1, stateDay5Final.daily.completedCount)
    assertEquals(1, stateDay5Final.streak.current) // Restarted to 1
    assertEquals(3, stateDay5Final.streak.best) // Best 3 remains intact!
    assertEquals("2026-09-01", stateDay5Final.streak.lastActivityDate)
  }

  @Test
  fun testFailQuestUpdatesDailyFailureCountWithoutBreakingStreak() = runTest {
    // Set active streak on Day 1
    clock.setTime(1787824800000L, "2026-08-28T10:00:00Z", "2026-08-28")
    val q1Result = controller.dispatch(Command.CreateQuest(title = "Q1")) as CommandResult.Success
    val q1Id = q1Result.state.quests.order.first()
    controller.dispatch(Command.CompleteQuest(q1Id))

    // Fail a second quest on same day
    val q2Result = controller.dispatch(Command.CreateQuest(title = "Q2")) as CommandResult.Success
    val q2Id = q2Result.state.quests.order.last()
    val failResult = controller.dispatch(Command.FailQuest(q2Id))
    assertTrue(failResult is CommandResult.Success)
    val state = (failResult as CommandResult.Success).state

    assertEquals(1, state.daily.completedCount)
    assertEquals(1, state.daily.failedCount)
    assertEquals(1, state.progression.totalFailed)
    assertEquals(1, state.streak.current) // Streak is NOT broken by quest failure
  }

  @Test
  fun testPersistenceRoundTripWithDailyAndStreak() = runTest {
    clock.setTime(1787824800000L, "2026-08-28T10:00:00Z", "2026-08-28")
    val qResult = controller.dispatch(Command.CreateQuest(title = "Persistence Quest")) as CommandResult.Success
    val qId = qResult.state.quests.order.first()
    controller.dispatch(Command.CompleteQuest(qId))

    val loadedState = fakeRepository.loadState().getOrThrow()
    assertEquals("2026-08-28", loadedState.daily.activeDate)
    assertEquals(1, loadedState.daily.completedCount)
    assertEquals(50, loadedState.daily.earnedXp)
    assertEquals(1, loadedState.streak.current)
    assertEquals(1, loadedState.streak.best)
    assertEquals("2026-08-28", loadedState.streak.lastActivityDate)
  }

  @Test
  fun testStateIsolationDuringDailyAndStreakUpdates() = runTest {
    val initialProfile = controller.state.value.profile
    val initialSettings = controller.state.value.settings

    val qResult = controller.dispatch(Command.CreateQuest(title = "Isolation Quest")) as CommandResult.Success
    val qId = qResult.state.quests.order.first()
    val completeResult = controller.dispatch(Command.CompleteQuest(qId)) as CommandResult.Success

    val postState = completeResult.state
    assertEquals(initialProfile, postState.profile)
    assertEquals(initialSettings, postState.settings)
  }

  @Test
  fun testDailyAndStreakSelectors() {
    val state = AppState.createDefault(
      currentDate = "2026-08-28",
      currentTimestamp = "2026-08-28T10:00:00Z"
    ).copy(
      daily = DailyRecord(
        activeDate = "2026-08-28",
        loginDates = listOf("2026-08-28"),
        completedCount = 4,
        failedCount = 1,
        earnedXp = 200,
        earnedGold = 40
      ),
      streak = Streak(
        current = 5,
        best = 10,
        lastActivityDate = "2026-08-28",
        shieldCount = 0
      )
    )

    assertEquals(4, Selectors.dailyCompletedCount(state))
    assertEquals(1, Selectors.dailyFailedCount(state))
    assertEquals(200, Selectors.dailyEarnedXp(state))
    assertEquals(5, Selectors.currentStreak(state))
    assertEquals(10, Selectors.bestStreak(state))
    assertTrue(Selectors.hasStreak(state))
    assertTrue(Selectors.isStreakActiveToday(state, "2026-08-28"))
    assertFalse(Selectors.isStreakAtRisk(state, "2026-08-28"))
    assertTrue(Selectors.isStreakAtRisk(state, "2026-08-29"))
  }
}
