package com.example.huntersystem

import com.example.huntersystem.application.AppController
import com.example.huntersystem.application.Command
import com.example.huntersystem.application.CommandResult
import com.example.huntersystem.application.DomainError
import com.example.huntersystem.application.EventBus
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.DeterministicIdGenerator
import com.example.huntersystem.infrastructure.TestClockPort
import com.example.huntersystem.state.AppState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandPipelineTest {

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
    clock = TestClockPort()
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
  fun testUpdateDisplayNameSuccess() = runTest {
    val result = controller.dispatch(Command.UpdateDisplayName("Cha Hae-In"))

    assertTrue(result is CommandResult.Success)
    val success = result as CommandResult.Success
    assertEquals("Cha Hae-In", success.state.profile.displayName)
    assertEquals(2L, success.state.metadata.revision)
    assertEquals(1, success.events.size)
    assertEquals("Cha Hae-In", fakeRepository.storedState.profile.displayName)
  }

  @Test
  fun testUpdateDisplayNameEmptyFailsValidation() = runTest {
    val result = controller.dispatch(Command.UpdateDisplayName("   "))

    assertTrue(result is CommandResult.Failure)
    val failure = result as CommandResult.Failure
    assertTrue(failure.error is DomainError.InvalidInput)
    // Confirm state was not mutated
    assertEquals("Sung Jinwoo", controller.state.value.profile.displayName)
  }

  @Test
  fun testResetProfileSuccess() = runTest {
    val result = controller.dispatch(Command.ResetProfile("Shadow Leader"))

    assertTrue(result is CommandResult.Success)
    val success = result as CommandResult.Success
    assertEquals("Shadow Leader", success.state.profile.displayName)
    assertEquals(2L, success.state.metadata.revision)
  }
}
