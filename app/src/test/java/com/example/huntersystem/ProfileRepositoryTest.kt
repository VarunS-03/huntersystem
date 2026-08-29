package com.example.huntersystem

import com.example.huntersystem.data.repository.JsonFileProfileRepository
import com.example.huntersystem.state.AppState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testSaveAndLoadStateRoundTrip() = runTest {
    val dir = tempFolder.newFolder("profile_test")
    val repo = JsonFileProfileRepository(dir)

    val state = AppState.createDefault().copy(
      progression = AppState.createDefault().progression.copy(level = 5, xp = 450, rank = "C")
    )

    val saveResult = repo.saveState(state)
    assertTrue(saveResult.isSuccess)

    val loadResult = repo.loadState()
    assertTrue(loadResult.isSuccess)
    val loadedState = loadResult.getOrThrow()

    assertEquals(5, loadedState.progression.level)
    assertEquals(450, loadedState.progression.xp)
    assertEquals("C", loadedState.progression.rank)
  }

  @Test
  fun testCorruptStateRecoversToBackupOrDefault() = runTest {
    val dir = tempFolder.newFolder("corrupt_test")
    val repo = JsonFileProfileRepository(dir)

    // Write corrupted JSON directly
    val targetFile = File(dir, "hunter_system_profile_v1.json")
    targetFile.writeText("{ invalid json", Charsets.UTF_8)

    val loadResult = repo.loadState()
    assertTrue(loadResult.isSuccess)
    // Recovers safely to a valid default state
    assertEquals(1, loadResult.getOrThrow().progression.level)
  }

  @Test
  fun testExportAndImportState() = runTest {
    val dir = tempFolder.newFolder("export_test")
    val repo = JsonFileProfileRepository(dir)

    val state = AppState.createDefault().copy(
      profile = AppState.createDefault().profile.copy(displayName = "Beru")
    )
    repo.saveState(state)

    val exportResult = repo.exportState()
    assertTrue(exportResult.isSuccess)
    val serialized = exportResult.getOrThrow()
    assertTrue(serialized.contains("Beru"))

    val importResult = repo.importState(serialized)
    assertTrue(importResult.isSuccess)
    assertEquals("Beru", importResult.getOrThrow().profile.displayName)
  }

  @Test
  fun testSaveAndLoadQuestsRoundTrip() = runTest {
    val dir = tempFolder.newFolder("quest_repo_test")
    val repo = JsonFileProfileRepository(dir)

    val quest = com.example.huntersystem.domain.model.Quest(
      id = "quest_101",
      title = "Clear Red Gate",
      description = "Defeat Ice Elves",
      tier = com.example.huntersystem.domain.model.QuestTier.S,
      status = com.example.huntersystem.domain.model.QuestStatus.ACTIVE,
      createdAt = "2026-08-29T10:00:00Z",
      updatedAt = "2026-08-29T10:00:00Z",
      startedAt = "2026-08-29T10:00:00Z",
      reward = com.example.huntersystem.domain.model.QuestReward(250, 100),
      penalty = com.example.huntersystem.domain.model.QuestPenalty(100, 50)
    )

    val state = AppState.createDefault().copy(
      quests = com.example.huntersystem.state.QuestsState(
        byId = mapOf(quest.id to quest),
        order = listOf(quest.id)
      )
    )

    repo.saveState(state)
    val loaded = repo.loadState().getOrThrow()

    assertEquals(1, loaded.quests.order.size)
    assertEquals("quest_101", loaded.quests.order.first())
    val loadedQuest = loaded.quests.byId["quest_101"]
    org.junit.Assert.assertNotNull(loadedQuest)
    assertEquals("Clear Red Gate", loadedQuest?.title)
    assertEquals(com.example.huntersystem.domain.model.QuestTier.S, loadedQuest?.tier)
    assertEquals(com.example.huntersystem.domain.model.QuestStatus.ACTIVE, loadedQuest?.status)
    assertEquals(250, loadedQuest?.reward?.xp)
  }
}
