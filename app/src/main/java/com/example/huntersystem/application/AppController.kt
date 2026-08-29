package com.example.huntersystem.application

import com.example.huntersystem.domain.engine.AchievementEngine
import com.example.huntersystem.domain.engine.DailyEngine
import com.example.huntersystem.domain.engine.ProgressionEngine
import com.example.huntersystem.domain.engine.RewardEngine
import com.example.huntersystem.domain.engine.StreakEngine
import com.example.huntersystem.domain.events.DomainEvent
import com.example.huntersystem.domain.model.Metadata
import com.example.huntersystem.domain.model.Profile
import com.example.huntersystem.domain.model.Quest
import com.example.huntersystem.domain.model.QuestStatus
import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.infrastructure.ClockPort
import com.example.huntersystem.infrastructure.IdGenerator
import com.example.huntersystem.state.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppController(
  private val repository: ProfileRepository,
  private val eventBus: EventBus,
  private val clock: ClockPort,
  private val idGenerator: IdGenerator,
  initialState: AppState = AppState.createDefault()
) {
  private val transactionMutex = Mutex()
  private val _state = MutableStateFlow(initialState)
  val state: StateFlow<AppState> = _state.asStateFlow()

  suspend fun dispatch(command: Command): CommandResult = transactionMutex.withLock {
    val currentState = _state.value
    val timestamp = clock.currentIsoTimestamp()
    val localDate = clock.currentLocalDate()

    val result: Result<Pair<AppState, List<DomainEvent>>> = when (command) {
      is Command.LoadState -> {
        repository.loadState().map { loadedState ->
          val dailyResolution = DailyEngine.resolveDay(loadedState.daily, localDate)
          val streakRollover = StreakEngine.evaluateStreakOnRollover(loadedState.streak, localDate)
          val normalizedState = if (dailyResolution.isRollover || streakRollover.isBroken) {
            loadedState.copy(
              daily = dailyResolution.updatedDaily,
              streak = streakRollover.updatedStreak
            )
          } else {
            loadedState
          }
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.StateLoaded(
            id = eventId,
            occurredAt = timestamp,
            revision = normalizedState.metadata.revision
          )
          normalizedState to listOf(event)
        }
      }

      is Command.ResetProfile -> {
        val newProfileId = idGenerator.generateId("hunter")
        val resetState = AppState.createDefault(
          profileId = newProfileId,
          displayName = command.displayName.trim().ifEmpty { "Sung Jinwoo" },
          currentDate = localDate,
          currentTimestamp = timestamp
        ).copy(
          metadata = Metadata(
            lastSavedAt = timestamp,
            lastEventId = idGenerator.generateId("evt"),
            revision = currentState.metadata.revision + 1
          )
        )

        repository.clearState().map {
          repository.saveState(resetState)
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.ProfileReset(id = eventId, occurredAt = timestamp)
          resetState to listOf(event)
        }
      }

      is Command.CreateProfile -> {
        val trimmed = command.displayName.trim()
        if (trimmed.isEmpty()) {
          Result.failure(IllegalArgumentException("Display name cannot be empty"))
        } else {
          val newProfileId = idGenerator.generateId("hunter")
          val updatedProfile = Profile(
            id = newProfileId,
            displayName = trimmed,
            createdAt = timestamp,
            updatedAt = timestamp
          )
          val newState = currentState.copy(
            profile = updatedProfile,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.ProfileCreated(
            id = eventId,
            occurredAt = timestamp,
            profileId = newProfileId,
            displayName = trimmed
          )
          Result.success(newState to listOf(event))
        }
      }

      is Command.UpdateDisplayName -> {
        val trimmed = command.displayName.trim()
        if (trimmed.isEmpty()) {
          Result.failure(IllegalArgumentException("Display name cannot be empty"))
        } else {
          val updatedProfile = currentState.profile.copy(
            displayName = trimmed,
            updatedAt = timestamp
          )
          val newState = currentState.copy(
            profile = updatedProfile,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.ProfileUpdated(
            id = eventId,
            occurredAt = timestamp,
            profileId = currentState.profile.id,
            displayName = trimmed
          )
          Result.success(newState to listOf(event))
        }
      }

      is Command.UpdateSettings -> {
        val newState = currentState.copy(
          settings = command.settings,
          metadata = currentState.metadata.copy(
            lastSavedAt = timestamp,
            revision = currentState.metadata.revision + 1
          )
        )
        val eventId = idGenerator.generateId("evt")
        val event = DomainEvent.SettingsUpdated(id = eventId, occurredAt = timestamp)
        Result.success(newState to listOf(event))
      }

      is Command.ResolveDailyCycle -> {
        val dailyResolution = DailyEngine.resolveDay(currentState.daily, localDate)
        val streakRollover = StreakEngine.evaluateStreakOnRollover(currentState.streak, localDate)

        if (!dailyResolution.isRollover && !streakRollover.isBroken) {
          // Idempotent: same date resolution produces no state mutation
          Result.success(currentState to emptyList())
        } else {
          val newState = currentState.copy(
            daily = dailyResolution.updatedDaily,
            streak = streakRollover.updatedStreak,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val events = mutableListOf<DomainEvent>()
          if (dailyResolution.isRollover) {
            events.add(
              DomainEvent.DayStarted(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                activeDate = localDate
              )
            )
          }
          if (streakRollover.isBroken) {
            events.add(
              DomainEvent.StreakBroken(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                date = localDate,
                previousStreak = streakRollover.previousStreak
              )
            )
          }
          Result.success(newState to events)
        }
      }

      is Command.GainXp -> {
        if (command.amount <= 0) {
          Result.failure(IllegalArgumentException("XP gain amount must be positive"))
        } else {
          val calc = ProgressionEngine.applyXpGain(
            currentProgression = currentState.progression,
            xpGained = command.amount,
            questCompleted = false
          )
          val newState = currentState.copy(
            progression = calc.updatedProgression,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )

          val events = mutableListOf<DomainEvent>()
          events.add(
            DomainEvent.XpGained(
              id = idGenerator.generateId("evt"),
              occurredAt = timestamp,
              amount = command.amount,
              totalXp = calc.updatedProgression.xp,
              currentLevel = calc.updatedProgression.level
            )
          )

          if (calc.levelsGained > 0) {
            events.add(
              DomainEvent.LevelUp(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                oldLevel = calc.oldLevel,
                newLevel = calc.newLevel
              )
            )
          }

          if (calc.rankPromoted) {
            events.add(
              DomainEvent.RankPromoted(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                oldRank = calc.oldRank,
                newRank = calc.newRank
              )
            )
          }

          Result.success(newState to events)
        }
      }

      is Command.GainGold -> {
        if (command.amount <= 0) {
          Result.failure(IllegalArgumentException("Gold gain amount must be positive"))
        } else {
          val rewardCalc = RewardEngine.applyDirectGain(
            currentGold = currentState.progression.gold,
            amount = command.amount
          )
          val updatedProgression = currentState.progression.copy(
            gold = rewardCalc.updatedGold
          )
          val newState = currentState.copy(
            progression = updatedProgression,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val event = DomainEvent.GoldEarned(
            id = idGenerator.generateId("evt"),
            occurredAt = timestamp,
            amount = rewardCalc.delta,
            resultingBalance = rewardCalc.updatedGold,
            source = "manual_grant"
          )
          Result.success(newState to listOf(event))
        }
      }

      is Command.CreateQuest -> {
        val trimmedTitle = command.title.trim()
        if (trimmedTitle.isEmpty()) {
          Result.failure(IllegalArgumentException("Quest title cannot be empty"))
        } else if (trimmedTitle.length > 100) {
          Result.failure(IllegalArgumentException("Quest title cannot exceed 100 characters"))
        } else {
          val questId = idGenerator.generateId("quest")
          val status = if (command.startImmediately) QuestStatus.ACTIVE else QuestStatus.PENDING
          val startedAt = if (command.startImmediately) timestamp else null
          val quest = Quest(
            id = questId,
            title = trimmedTitle,
            description = command.description.trim(),
            tier = command.tier,
            status = status,
            createdAt = timestamp,
            updatedAt = timestamp,
            startedAt = startedAt,
            dueDate = command.dueDate,
            reward = command.tier.baseReward,
            penalty = command.tier.basePenalty
          )

          val newQuestsState = currentState.quests.copy(
            byId = currentState.quests.byId + (questId to quest),
            order = currentState.quests.order + questId
          )
          val newState = currentState.copy(
            quests = newQuestsState,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.QuestCreated(id = eventId, occurredAt = timestamp, quest = quest)
          Result.success(newState to listOf(event))
        }
      }

      is Command.StartQuest -> {
        val quest = currentState.quests.byId[command.questId]
        if (quest == null) {
          Result.failure(NoSuchElementException("Quest with ID '${command.questId}' not found"))
        } else if (quest.status == QuestStatus.ACTIVE) {
          Result.success(currentState to emptyList())
        } else if (quest.status != QuestStatus.PENDING) {
          Result.failure(IllegalStateException("Cannot start quest in '${quest.status.value}' status"))
        } else {
          val updatedQuest = quest.copy(
            status = QuestStatus.ACTIVE,
            startedAt = timestamp,
            updatedAt = timestamp
          )
          val newQuestsState = currentState.quests.copy(
            byId = currentState.quests.byId + (command.questId to updatedQuest)
          )
          val newState = currentState.copy(
            quests = newQuestsState,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.QuestStarted(id = eventId, occurredAt = timestamp, questId = command.questId)
          Result.success(newState to listOf(event))
        }
      }

      is Command.CompleteQuest -> {
        val quest = currentState.quests.byId[command.questId]
        if (quest == null) {
          Result.failure(NoSuchElementException("Quest with ID '${command.questId}' not found"))
        } else if (quest.status.isTerminal) {
          Result.failure(IllegalStateException("Quest '${command.questId}' is already in terminal state '${quest.status.value}'"))
        } else {
          val updatedQuest = quest.copy(
            status = QuestStatus.COMPLETED,
            completedAt = timestamp,
            updatedAt = timestamp
          )
          val newQuestsState = currentState.quests.copy(
            byId = currentState.quests.byId + (command.questId to updatedQuest)
          )

          // 1. Resolve Daily Rollover if date changed
          val dailyResolution = DailyEngine.resolveDay(currentState.daily, localDate)
          val activeDaily = dailyResolution.updatedDaily

          // 2. Resolve Streak Rollover if date changed
          val streakRollover = StreakEngine.evaluateStreakOnRollover(currentState.streak, localDate)
          val activeStreak = streakRollover.updatedStreak

          // 3. Apply XP Gain via ProgressionEngine
          val progressionCalc = ProgressionEngine.applyXpGain(
            currentProgression = currentState.progression,
            xpGained = quest.reward.xp,
            questCompleted = true
          )

          // 4. Apply Reward/Economy via RewardEngine
          val rewardCalc = RewardEngine.applyReward(
            currentGold = currentState.progression.gold,
            rewardGold = quest.reward.gold
          )
          val finalProgression = progressionCalc.updatedProgression.copy(
            gold = rewardCalc.updatedGold
          )

          // 5. Update Daily Record via DailyEngine
          val updatedDaily = DailyEngine.recordQuestCompletion(
            currentDaily = activeDaily,
            currentDate = localDate,
            xpGained = quest.reward.xp,
            goldGained = rewardCalc.delta
          )

          // 6. Update Streak via StreakEngine
          val streakAction = StreakEngine.recordQualifyingActivity(
            currentStreak = activeStreak,
            currentDate = localDate
          )

          val newState = currentState.copy(
            quests = newQuestsState,
            progression = finalProgression,
            daily = updatedDaily,
            streak = streakAction.updatedStreak,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )

          val events = mutableListOf<DomainEvent>()

          if (dailyResolution.isRollover) {
            events.add(
              DomainEvent.DayStarted(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                activeDate = localDate
              )
            )
          }

          if (streakRollover.isBroken && !streakAction.isStarted) {
            events.add(
              DomainEvent.StreakBroken(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                date = localDate,
                previousStreak = streakRollover.previousStreak
              )
            )
          }

          events.add(
            DomainEvent.QuestCompleted(
              id = idGenerator.generateId("evt"),
              occurredAt = timestamp,
              questId = command.questId,
              reward = quest.reward
            )
          )

          events.add(
            DomainEvent.XpGained(
              id = idGenerator.generateId("evt"),
              occurredAt = timestamp,
              amount = quest.reward.xp,
              totalXp = finalProgression.xp,
              currentLevel = finalProgression.level
            )
          )

          if (rewardCalc.delta > 0) {
            events.add(
              DomainEvent.GoldEarned(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                amount = rewardCalc.delta,
                resultingBalance = finalProgression.gold,
                source = "quest_completion"
              )
            )
          }

          if (progressionCalc.levelsGained > 0) {
            events.add(
              DomainEvent.LevelUp(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                oldLevel = progressionCalc.oldLevel,
                newLevel = progressionCalc.newLevel
              )
            )
          }

          if (progressionCalc.rankPromoted) {
            events.add(
              DomainEvent.RankPromoted(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                oldRank = progressionCalc.oldRank,
                newRank = progressionCalc.newRank
              )
            )
          }

          if (streakAction.isStarted) {
            events.add(
              DomainEvent.StreakStarted(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                date = localDate,
                streakCount = streakAction.updatedStreak.current
              )
            )
          } else if (streakAction.isContinued) {
            events.add(
              DomainEvent.StreakContinued(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                date = localDate,
                streakCount = streakAction.updatedStreak.current,
                isBest = streakAction.isBestNew
              )
            )
          }

          Result.success(newState to events)
        }
      }

      is Command.FailQuest -> {
        val quest = currentState.quests.byId[command.questId]
        if (quest == null) {
          Result.failure(NoSuchElementException("Quest with ID '${command.questId}' not found"))
        } else if (quest.status.isTerminal) {
          Result.failure(IllegalStateException("Quest '${command.questId}' is already in terminal state '${quest.status.value}'"))
        } else {
          val updatedQuest = quest.copy(
            status = QuestStatus.FAILED,
            failedAt = timestamp,
            updatedAt = timestamp
          )
          val newQuestsState = currentState.quests.copy(
            byId = currentState.quests.byId + (command.questId to updatedQuest)
          )

          val dailyResolution = DailyEngine.resolveDay(currentState.daily, localDate)
          val activeDaily = dailyResolution.updatedDaily

          val penaltyCalc = RewardEngine.applyPenalty(
            currentGold = currentState.progression.gold,
            penaltyGold = quest.penalty.gold
          )
          val updatedProgression = ProgressionEngine.recordQuestFailure(currentState.progression).copy(
            gold = penaltyCalc.updatedGold
          )
          val updatedDaily = DailyEngine.recordQuestFailure(activeDaily, localDate)

          val newState = currentState.copy(
            quests = newQuestsState,
            progression = updatedProgression,
            daily = updatedDaily,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val events = mutableListOf<DomainEvent>()
          events.add(
            DomainEvent.QuestFailed(
              id = idGenerator.generateId("evt"),
              occurredAt = timestamp,
              questId = command.questId
            )
          )
          if (penaltyCalc.delta < 0) {
            events.add(
              DomainEvent.GoldPenaltyApplied(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                amount = -penaltyCalc.delta,
                resultingBalance = penaltyCalc.updatedGold,
                source = "quest_failure"
              )
            )
          }
          Result.success(newState to events)
        }
      }

      is Command.CancelQuest,
      is Command.AbandonQuest -> {
        val questId = if (command is Command.CancelQuest) command.questId else (command as Command.AbandonQuest).questId
        val quest = currentState.quests.byId[questId]
        if (quest == null) {
          Result.failure(NoSuchElementException("Quest with ID '$questId' not found"))
        } else if (quest.status.isTerminal) {
          Result.failure(IllegalStateException("Quest '$questId' is already in terminal state '${quest.status.value}'"))
        } else {
          val updatedQuest = quest.copy(
            status = QuestStatus.CANCELLED,
            cancelledAt = timestamp,
            updatedAt = timestamp
          )
          val newQuestsState = currentState.quests.copy(
            byId = currentState.quests.byId + (questId to updatedQuest)
          )
          val newState = currentState.copy(
            quests = newQuestsState,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val eventId = idGenerator.generateId("evt")
          val event = if (command is Command.CancelQuest) {
            DomainEvent.QuestCancelled(id = eventId, occurredAt = timestamp, questId = questId)
          } else {
            DomainEvent.QuestAbandoned(id = eventId, occurredAt = timestamp, questId = questId)
          }
          Result.success(newState to listOf(event))
        }
      }

      is Command.DeleteQuest -> {
        val quest = currentState.quests.byId[command.questId]
        if (quest == null) {
          Result.failure(NoSuchElementException("Quest with ID '${command.questId}' not found"))
        } else {
          val newQuestsState = currentState.quests.copy(
            byId = currentState.quests.byId - command.questId,
            order = currentState.quests.order - command.questId
          )
          val newState = currentState.copy(
            quests = newQuestsState,
            metadata = currentState.metadata.copy(
              lastSavedAt = timestamp,
              revision = currentState.metadata.revision + 1
            )
          )
          val eventId = idGenerator.generateId("evt")
          val event = DomainEvent.QuestDeleted(id = eventId, occurredAt = timestamp, questId = command.questId)
          Result.success(newState to listOf(event))
        }
      }
    }

    result.fold(
      onSuccess = { (candidateState, baseEvents) ->
        // Evaluate Achievements deterministically on the candidate AppState
        val achievementEval = AchievementEngine.evaluate(candidateState, timestamp)
        val finalState = if (achievementEval.newlyUnlockedIds.isNotEmpty()) {
          candidateState.copy(achievements = achievementEval.updatedAchievements)
        } else {
          candidateState
        }

        val allEvents = baseEvents.toMutableList()
        for (unlockedId in achievementEval.newlyUnlockedIds) {
          allEvents.add(
            DomainEvent.AchievementUnlocked(
              id = idGenerator.generateId("evt"),
              occurredAt = timestamp,
              achievementId = unlockedId
            )
          )
        }

        if (allEvents.isNotEmpty()) {
          // Persist the state after successful transaction
          val saveResult = repository.saveState(finalState)
          if (saveResult.isFailure) {
            val errorMsg = saveResult.exceptionOrNull()?.message ?: "Persistence failed"
            eventBus.emit(
              DomainEvent.PersistenceFailed(
                id = idGenerator.generateId("evt"),
                occurredAt = timestamp,
                reason = errorMsg
              )
            )
            return@withLock CommandResult.Failure(DomainError.PersistenceFailure(errorMsg))
          }
          _state.value = finalState
          eventBus.emitAll(allEvents)
        }
        CommandResult.Success(state = finalState, events = allEvents)
      },
      onFailure = { throwable ->
        val domainError = when (throwable) {
          is IllegalArgumentException -> DomainError.InvalidInput(throwable.message ?: "Invalid input")
          is NoSuchElementException -> DomainError.NotFound(throwable.message ?: "Resource not found")
          is IllegalStateException -> DomainError.InvalidStateTransition(throwable.message ?: "Invalid state transition")
          is UnsupportedOperationException -> DomainError.InvalidStateTransition(throwable.message ?: "Unsupported operation")
          else -> DomainError.UnknownError(throwable.message ?: "Unknown transaction failure")
        }
        CommandResult.Failure(error = domainError)
      }
    )
  }
}
