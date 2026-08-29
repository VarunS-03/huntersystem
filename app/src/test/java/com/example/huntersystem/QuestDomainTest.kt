package com.example.huntersystem

import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.DomainError
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.model.QuestStatus
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.DeterministicIdGenerator
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.Selectors
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuestDomainTest {

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
  fun testCreateQuestSuccess_DefaultActive() = runTest {
    val command = Command.CreateQuest(
      title = "Clear D-Rank Dungeon",
      description = "Eliminate goblin chieftain",
      tier = QuestTier.H,
      startImmediately = true
    )

    val result = controller.dispatch(command)
    assertTrue(result is CommandResult.Success)
    val success = result as CommandResult.Success

    assertEquals(1, success.state.quests.order.size)
    val questId = success.state.quests.order.first()
    val quest = success.state.quests.byId[questId]
    assertNotNull(quest)
    assertEquals("Clear D-Rank Dungeon", quest?.title)
    assertEquals("Eliminate goblin chieftain", quest?.description)
    assertEquals(QuestTier.H, quest?.tier)
    assertEquals(QuestStatus.ACTIVE, quest?.status)
    assertEquals("2026-08-29T10:00:00Z", quest?.createdAt)
    assertEquals("2026-08-29T10:00:00Z", quest?.startedAt)
    assertEquals(100, quest?.reward?.xp)
    assertEquals(50, quest?.reward?.gold)

    // Verify event
    assertEquals(1, success.events.size)
    val event = success.events.first()
    assertTrue(event is DomainEvent.QuestCreated)
    assertEquals(questId, (event as DomainEvent.QuestCreated).quest.id)
  }

  @Test
  fun testCreateQuestPending() = runTest {
    val command = Command.CreateQuest(
      title = "Daily 10km Run",
      tier = QuestTier.N,
      startImmediately = false
    )

    val result = controller.dispatch(command)
    assertTrue(result is CommandResult.Success)
    val success = result as CommandResult.Success

    val questId = success.state.quests.order.first()
    val quest = success.state.quests.byId[questId]
    assertNotNull(quest)
    assertEquals(QuestStatus.PENDING, quest?.status)
    assertNull(quest?.startedAt)
  }

  @Test
  fun testCreateQuestValidationFailure_EmptyTitle() = runTest {
    val command = Command.CreateQuest(
      title = "   ",
      tier = QuestTier.E
    )

    val result = controller.dispatch(command)
    assertTrue(result is CommandResult.Failure)
    val failure = result as CommandResult.Failure
    assertTrue(failure.error is DomainError.InvalidInput)
    assertEquals(0, controller.state.value.quests.order.size)
  }

  @Test
  fun testStartQuestSuccess() = runTest {
    // 1. Create pending quest
    val createResult = controller.dispatch(
      Command.CreateQuest(
        title = "Shadow Soldier Extraction",
        tier = QuestTier.S,
        startImmediately = false
      )
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    // Advance clock
    clock.advanceTime(minutes = 5)

    // 2. Start quest
    val startResult = controller.dispatch(Command.StartQuest(questId))
    assertTrue(startResult is CommandResult.Success)
    val success = startResult as CommandResult.Success
    val quest = success.state.quests.byId[questId]
    assertNotNull(quest)
    assertEquals(QuestStatus.ACTIVE, quest?.status)
    assertEquals("2026-08-29T10:05:00Z", quest?.startedAt)

    // Verify event
    val event = success.events.first()
    assertTrue(event is DomainEvent.QuestStarted)
    assertEquals(questId, (event as DomainEvent.QuestStarted).questId)
  }

  @Test
  fun testCompleteQuestSuccess() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Physical Conditioning", tier = QuestTier.N)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    clock.advanceTime(minutes = 30)

    val completeResult = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(completeResult is CommandResult.Success)
    val success = completeResult as CommandResult.Success
    val quest = success.state.quests.byId[questId]
    assertNotNull(quest)
    assertEquals(QuestStatus.COMPLETED, quest?.status)
    assertEquals("2026-08-29T10:30:00Z", quest?.completedAt)

    // Progression XP is awarded (Tier N gives 50 XP)
    assertEquals(50, success.state.progression.xp)
    assertEquals(1, success.state.progression.level)
    assertEquals(1, success.state.progression.totalCompleted)

    // Verify events
    val questCompletedEvent = success.events.first { it is DomainEvent.QuestCompleted } as DomainEvent.QuestCompleted
    assertEquals(questId, questCompletedEvent.questId)
    assertEquals(50, questCompletedEvent.reward.xp)

    val xpGainedEvent = success.events.first { it is DomainEvent.XpGained } as DomainEvent.XpGained
    assertEquals(50, xpGainedEvent.amount)
    assertEquals(50, xpGainedEvent.totalXp)
  }

  @Test
  fun testFailQuestSuccess() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Red Gate Survival", tier = QuestTier.S)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    clock.advanceTime(hours = 2)

    val failResult = controller.dispatch(Command.FailQuest(questId))
    assertTrue(failResult is CommandResult.Success)
    val success = failResult as CommandResult.Success
    val quest = success.state.quests.byId[questId]
    assertNotNull(quest)
    assertEquals(QuestStatus.FAILED, quest?.status)
    assertEquals("2026-08-29T12:00:00Z", quest?.failedAt)

    // Verify event
    val event = success.events.first()
    assertTrue(event is DomainEvent.QuestFailed)
    assertEquals(questId, (event as DomainEvent.QuestFailed).questId)
  }

  @Test
  fun testCancelQuestSuccess() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Optional Gathering", tier = QuestTier.E)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    val cancelResult = controller.dispatch(Command.CancelQuest(questId))
    assertTrue(cancelResult is CommandResult.Success)
    val success = cancelResult as CommandResult.Success
    val quest = success.state.quests.byId[questId]
    assertNotNull(quest)
    assertEquals(QuestStatus.CANCELLED, quest?.status)
    assertNotNull(quest?.cancelledAt)

    val event = success.events.first()
    assertTrue(event is DomainEvent.QuestCancelled)
    assertEquals(questId, (event as DomainEvent.QuestCancelled).questId)
  }

  @Test
  fun testTerminalQuestCannotTransitionAgain() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Solo Dungeon", tier = QuestTier.N)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    controller.dispatch(Command.CompleteQuest(questId))

    // Attempting to fail or complete an already completed quest fails
    val secondComplete = controller.dispatch(Command.CompleteQuest(questId))
    assertTrue(secondComplete is CommandResult.Failure)
    assertTrue((secondComplete as CommandResult.Failure).error is DomainError.InvalidStateTransition)

    val attemptFail = controller.dispatch(Command.FailQuest(questId))
    assertTrue(attemptFail is CommandResult.Failure)
    assertTrue((attemptFail as CommandResult.Failure).error is DomainError.InvalidStateTransition)
  }

  @Test
  fun testDeleteQuestSuccess() = runTest {
    val createResult = controller.dispatch(
      Command.CreateQuest(title = "Temporary Objective", tier = QuestTier.E)
    ) as CommandResult.Success
    val questId = createResult.state.quests.order.first()

    val deleteResult = controller.dispatch(Command.DeleteQuest(questId))
    assertTrue(deleteResult is CommandResult.Success)
    val success = deleteResult as CommandResult.Success
    assertEquals(0, success.state.quests.order.size)
    assertNull(success.state.quests.byId[questId])

    val event = success.events.first()
    assertTrue(event is DomainEvent.QuestDeleted)
    assertEquals(questId, (event as DomainEvent.QuestDeleted).questId)
  }

  @Test
  fun testSelectorsFiltering() = runTest {
    controller.dispatch(Command.CreateQuest("Active 1", tier = QuestTier.E, startImmediately = true))
    controller.dispatch(Command.CreateQuest("Pending 1", tier = QuestTier.N, startImmediately = false))
    val create3 = controller.dispatch(Command.CreateQuest("Completed 1", tier = QuestTier.H, startImmediately = true)) as CommandResult.Success
    val quest3Id = create3.state.quests.order.last()
    controller.dispatch(Command.CompleteQuest(quest3Id))

    val currentState = controller.state.value

    assertEquals(3, Selectors.allQuests(currentState).size)
    assertEquals(1, Selectors.activeQuests(currentState).size)
    assertEquals(1, Selectors.pendingQuests(currentState).size)
    assertEquals(1, Selectors.completedQuests(currentState).size)
  }
}
