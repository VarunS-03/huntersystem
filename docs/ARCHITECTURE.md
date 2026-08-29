# Hunter System — Architecture

> Documents the **actual** architecture as implemented. Not an idealized target design.

---

## Layers

| Layer | Package | Responsibility |
|-------|---------|----------------|
| **Presentation** | `com.example`, `...presentation.*` | Compose UI, ViewModel, theme |
| **Application** | `...application.*` | Command dispatch, orchestration, events |
| **State** | `...state.*` | `AppState`, derived selectors |
| **Domain** | `...domain.*` | Models, pure engines, events, repository interface |
| **Data** | `...data.repository.*` | JSON file persistence |
| **Infrastructure** | `...infrastructure.*` | Clock, ID generation |

There is no separate "use case" layer. `AppController` is the application service / orchestrator.

---

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt
├── ui/theme/                          # LEGACY — unused Android Studio template
└── huntersystem/
    ├── application/
    │   ├── AppController.kt           # Central orchestrator
    │   ├── Command.kt                 # Command sealed interface
    │   ├── CommandResult.kt           # Success / Failure
    │   ├── DomainError.kt             # Typed errors
    │   └── EventBus.kt                # Fact event transport
    ├── state/
    │   ├── AppState.kt                # Root state model
    │   └── Selectors.kt               # Derived read helpers
    ├── domain/
    │   ├── model/                     # Quest, Progression, DailyRecord, Streak, etc.
    │   ├── engine/                    # 5 pure calculation engines + AchievementEngine
    │   ├── events/DomainEvent.kt      # Sealed event types
    │   └── repository/ProfileRepository.kt
    ├── data/repository/
    │   └── JsonFileProfileRepository.kt
    ├── infrastructure/
    │   ├── ClockPort.kt               # Clock + TestClock + IdGenerator
    │   └── IdGenerator.kt             # typealias only
    └── presentation/
        ├── viewmodel/HunterViewModel.kt
        ├── screens/DashboardShellScreen.kt
        └── theme/                     # HunterSystemTheme (ACTIVE)
```

**Kotlin source files (main):** 35  
**Test files:** 14 (13 unit + 1 instrumented)

---

## AppState

Root immutable data class. Schema version: `1`.

```kotlin
data class AppState(
  val schemaVersion: Int,
  val profile: Profile,
  val progression: Progression,
  val quests: QuestsState,      // byId Map + order List
  val streak: Streak,
  val daily: DailyRecord,
  val achievements: Achievements,
  val settings: Settings,
  val metadata: Metadata
)
```

Factory: `AppState.createDefault(profileId, displayName, currentDate, currentTimestamp)`.

Held in memory by `AppController` as `StateFlow<AppState>`.

---

## AppController

**Class:** `AppController(repository, eventBus, clock, idGenerator, initialState)`

**Concurrency:** `Mutex` — one transaction at a time via `transactionMutex.withLock`.

**Public API:**
- `val state: StateFlow<AppState>`
- `suspend fun dispatch(command: Command): CommandResult`

**Dependencies:** `ProfileRepository`, `EventBus`, `ClockPort`, `IdGenerator`

---

## Commands

Defined in `Command.kt` sealed interface:

| Command | Purpose |
|---------|---------|
| `LoadState` | Load from repository; normalize daily/streak on date change |
| `ResetProfile` | Clear + create fresh default profile |
| `CreateProfile` | Set new profile (validation: non-empty name) |
| `UpdateDisplayName` | Rename hunter |
| `UpdateSettings` | Replace settings object |
| `ResolveDailyCycle` | Idempotent day rollover + streak break check |
| `GainXp` | Manual XP grant (positive amount) |
| `GainGold` | Manual gold grant (positive amount) |
| `CreateQuest` | Create quest (optional immediate start) |
| `StartQuest` | PENDING → ACTIVE |
| `CompleteQuest` | Terminal complete + full reward pipeline |
| `FailQuest` | Terminal fail + penalty pipeline |
| `CancelQuest` | Terminal cancel (no rewards) |
| `AbandonQuest` | Same as cancel, different event |
| `DeleteQuest` | Remove from state (any status) |

**CommandResult:** `Success(state, events)` | `Failure(error: DomainError)`

**DomainError types:** `InvalidInput`, `NotFound`, `InvalidStateTransition`, `PersistenceFailure`, `UnknownError`

---

## Domain Engines

All are `object` singletons with pure functions (no I/O).

| Engine | File | Functions |
|--------|------|-----------|
| **ProgressionEngine** | `ProgressionEngine.kt` | `xpRequiredForLevel`, `rankForLevel`, `applyXpGain`, `recordQuestFailure` |
| **RewardEngine** | `RewardEngine.kt` | `applyReward`, `applyPenalty`, `applyDirectGain` |
| **DailyEngine** | `DailyEngine.kt` | `resolveDay`, `recordQuestCompletion`, `recordQuestFailure` |
| **StreakEngine** | `StreakEngine.kt` | `calculateDaysBetween`, `evaluateStreakOnRollover`, `recordQualifyingActivity` |
| **AchievementEngine** | `AchievementEngine.kt` | `evaluate(candidateState, timestamp)` |

### XP Policy
- Required XP for level L: `(100 * L^1.25).roundToInt()` (minimum level 1 → 100 XP)
- XP overflows into next level in a loop until below threshold
- `totalCompleted` incremented only when `questCompleted = true` in `applyXpGain`

### Rank Policy
- E: level 1–4, D: 5–9, C: 10–19, B: 20–29, A: 30–49, S: 50+

### Economy Policy
- Gold never below 0 (`coerceAtLeast(0)` on penalties)
- Negative reward/penalty amounts coerced to 0

### Streak Policy
- Qualifying activity: quest completion via `recordQualifyingActivity`
- Same-day idempotent (no double increment)
- Consecutive day (+1), gap >1 day resets to 1 (or breaks on rollover before activity)
- Rollover break: if `daysBetween(lastActivity, today) > 1` and `current > 0`

### Achievement Policy
- 7 definitions in `AchievementCatalog`
- Evaluated after every successful command path
- Idempotent: already-unlocked IDs skipped

---

## Selectors

`Selectors` object — read-only derived values. Does not mutate state.

Key functions:
- XP: `xpRequiredForNextLevel`, `xpProgressPercent`, `currentRank`
- Economy: `currentGold`, `canAfford`
- Daily: `dailyCompletedCount`, `dailyEarnedXp`, `dailyEarnedGold`
- Streak: `currentStreak`, `bestStreak`, `isStreakActiveToday`, `isStreakAtRisk`, `hasStreak`
- Quests: `allQuests`, `activeQuests`, `pendingQuests`, `completedQuests`, `failedQuests`, `cancelledQuests`, `questById`, `isQuestOverdue`, `nextActionCue`
- Achievements: `isAchievementUnlocked`, `unlockedAchievementCount`, `allAchievements`, `unlockedAchievements`, `lockedAchievements`, `achievementUnlockTimestamp`

---

## EventBus

**Class:** `EventBus`

- `SharedFlow<DomainEvent>` for coroutine collectors
- Synchronous `registerListener` / `emit` / `emitAll`
- Listener exceptions swallowed (transport must not crash)

**Semantics:** Fact notification only. No subscriber mutates application state.

**Emission timing:** After successful persistence, before returning `CommandResult.Success`.

**Exception:** `PersistenceFailed` emitted when save fails (state not committed).

---

## Persistence

**Implementation:** `JsonFileProfileRepository`

| Aspect | Behavior |
|--------|----------|
| Format | Moshi JSON of full `AppState` |
| File | `hunter_system_profile_v1.json` |
| Backup | `.bak` copy before overwrite |
| Write | temp file → rename (fallback copy+delete) |
| Load missing file | Create and save default state |
| Parse failure | Recover from backup → else default |
| IO | `Dispatchers.IO` |

**Interface:** `ProfileRepository` — `loadState`, `saveState`, `clearState`, `exportState`, `importState`

---

## ClockPort

**Interface:** `currentTimeMillis()`, `currentIsoTimestamp()` (UTC ISO), `currentLocalDate()` (device timezone)

**Implementations:**
- `SystemClockPort` — production (uses `System.currentTimeMillis()` internally)
- `TestClockPort` — fixed/advanceable time for tests

**Usage:** `AppController` reads clock once per transaction for `timestamp` and `localDate`.

**Exception:** `StreakEngine.calculateDaysBetween` uses `LocalDate.parse()` for date arithmetic (not "now").

---

## Presentation

```
MainActivity
  └── HunterViewModel (AndroidViewModel, factory creates dependencies)
        ├── controller.state → collectAsStateWithLifecycle
        └── dispatch(command) → viewModelScope.launch → controller.dispatch
              init: LoadState, ResolveDailyCycle
```

**Dependency wiring (in ViewModel factory):**
- `JsonFileProfileRepository(application.filesDir)`
- `EventBus()`, `SystemClockPort()`, `UuidGenerator()`
- `AppController(...)`

---

## UI

**Single screen:** `DashboardShellScreen(state, onDispatch)`

| Section | Purpose |
|---------|---------|
| SystemHeaderCard | App title/branding |
| PlayerHudCard | Name, gold, rank, level, XP bar, daily/streak stats |
| SystemDirectiveCard | `Selectors.nextActionCue` |
| QuestCreationCard | Title, description, tier, create button |
| QuestBoardCard | Filter tabs, quest list with actions |
| AchievementsCard | All 7 achievements locked/unlocked |
| FoundationDiagnosticsCard | Schema, revision, quest counts (Step 8 scaffolding) |
| FoundationActionsCard | Reset profile, update name (Step 8 scaffolding) |

**Quest actions per item:** START (pending), COMPLETE, FAIL, CANCEL, DELETE

**No navigation.** No settings screen. No onboarding.

---

## Transaction Lifecycle

Verified sequence in `AppController.dispatch()`:

```
1. transactionMutex.withLock
2. Read _state.value as currentState
3. Read clock.currentIsoTimestamp() and clock.currentLocalDate()
4. when(command) → Result<Pair<AppState, List<DomainEvent>>>
     a. Validation (empty title, invalid status, etc.)
     b. Pure engine calculations
     c. Build candidate AppState via .copy()
     d. Build baseEvents list
5. result.fold onSuccess:
     a. AchievementEngine.evaluate(candidateState, timestamp)
     b. Merge newly unlocked achievements into finalState
     c. Append AchievementUnlocked events to allEvents
     d. IF allEvents.isNotEmpty():
          i.   repository.saveState(finalState)
          ii.  ON FAILURE → emit PersistenceFailed → return Failure
          iii. ON SUCCESS → _state.value = finalState
          iv.  eventBus.emitAll(allEvents)
     e. Return CommandResult.Success(finalState, allEvents)
6. onFailure → map throwable to DomainError → CommandResult.Failure
```

### Exceptions / Edge Cases

| Case | Behavior |
|------|----------|
| Idempotent command (empty events, no achievements) | Returns Success; **does not** update `_state` or persist |
| `ResolveDailyCycle` same day | Empty events → no persist |
| `StartQuest` already ACTIVE | Empty events → no persist |
| `LoadState` | Always emits `StateLoaded` → always persists (even unchanged) |
| `LoadState` with rollover | Updates daily/streak but **does not bump** `metadata.revision` |
| Achievement unlock with empty base events | Achievement events trigger persist |
| Persistence failure | Memory unchanged; `PersistenceFailed` event emitted |

### CompleteQuest Pipeline (cross-domain orchestration)

Order inside command handler:
1. Mark quest COMPLETED
2. `DailyEngine.resolveDay`
3. `StreakEngine.evaluateStreakOnRollover`
4. `ProgressionEngine.applyXpGain` (questCompleted=true)
5. `RewardEngine.applyReward`
6. Merge gold into progression
7. `DailyEngine.recordQuestCompletion`
8. `StreakEngine.recordQualifyingActivity`
9. Build composite state + events
10. (Post-fold) Achievement evaluation

---

## Domain Ownership

### Quest

| | |
|---|---|
| **Owns** | `AppState.quests` (byId, order), individual `Quest` fields |
| **Reads** | Clock timestamps for lifecycle dates |
| **May mutate** | AppController on Create/Start/Complete/Fail/Cancel/Delete |
| **Must not mutate** | Engines (Quest has no dedicated engine; tier defaults in model) |

### Progression

| | |
|---|---|
| **Owns** | `AppState.progression` (xp, level, gold, rank, totalCompleted, totalFailed) |
| **Reads** | XP amounts from quest rewards or GainXp command |
| **May mutate** | `ProgressionEngine`, `RewardEngine` (gold only) via AppController |
| **Must not mutate** | UI, EventBus, Repository directly |

### Daily

| | |
|---|---|
| **Owns** | `AppState.daily` (activeDate, loginDates, counters, earnedXp, earnedGold) |
| **Reads** | `ClockPort.currentLocalDate()` |
| **May mutate** | `DailyEngine` via AppController |
| **Must not mutate** | StreakEngine, ProgressionEngine |

### Streak

| | |
|---|---|
| **Owns** | `AppState.streak` (current, best, lastActivityDate, shieldCount) |
| **Reads** | Local date strings |
| **May mutate** | `StreakEngine` via AppController |
| **Must not mutate** | DailyEngine (orchestrated separately) |

### Economy

| | |
|---|---|
| **Owns** | `AppState.progression.gold` (authoritative wallet) |
| **Reads** | Reward/penalty amounts from quests or GainGold |
| **May mutate** | `RewardEngine` via AppController |
| **Daily mirror** | `daily.earnedGold` tracks today's earnings (DailyEngine) |

### Achievements

| | |
|---|---|
| **Owns** | `AppState.achievements` (unlockedIds, unlockedTimestamps) |
| **Reads** | Full candidate AppState after command |
| **May mutate** | `AchievementEngine.evaluate` in post-command fold only |
| **Must not mutate** | Cannot trigger independently of AppController transaction |

---

## State Ownership (Field-Level)

### AppState root
| Field | Classification |
|-------|----------------|
| `schemaVersion` | Metadata / migration |
| `profile` | Authoritative |
| `progression` | Authoritative |
| `quests` | Authoritative |
| `streak` | Authoritative |
| `daily` | Authoritative |
| `achievements` | Authoritative |
| `settings` | Authoritative (defaults unused in UI) |
| `metadata` | Metadata |

### Progression
| Field | Classification | Notes |
|-------|----------------|-------|
| `xp` | Authoritative | Current level progress |
| `level` | Authoritative | |
| `gold` | Authoritative | Wallet — single source for economy |
| `rank` | Authoritative (stored) | **Also derivable** via `ProgressionEngine.rankForLevel(level)` — drift risk |
| `totalCompleted` | Authoritative | Lifetime; used by achievements |
| `totalFailed` | Authoritative | Lifetime |

### DailyRecord
| Field | Classification | Notes |
|-------|----------------|-------|
| `activeDate` | Authoritative | Resets counters on rollover |
| `loginDates` | Authoritative | Accumulated history |
| `completedCount` | Authoritative | Daily scope |
| `failedCount` | Authoritative | Daily scope |
| `earnedXp` | Authoritative | Daily scope (not wallet) |
| `earnedGold` | Authoritative | Daily scope (not wallet) |

### Streak
| Field | Classification | Notes |
|-------|----------------|-------|
| `current` | Authoritative | |
| `best` | Authoritative | |
| `lastActivityDate` | Authoritative | |
| `shieldCount` | **Reserved/future** | Never read or written by engines |

### QuestsState
| Field | Classification |
|-------|----------------|
| `byId` | Authoritative |
| `order` | Authoritative | Display order |

### Achievements
| Field | Classification |
|-------|----------------|
| `unlockedIds` | Authoritative |
| `unlockedTimestamps` | Authoritative |

### Metadata
| Field | Classification |
|-------|----------------|
| `lastSavedAt` | Metadata |
| `lastEventId` | Metadata (sparsely used) |
| `revision` | Metadata | Incremented on most mutations |

---

## Derived Values

Computed by Selectors or engines at read/calculation time — not stored separately:

- `xpProgressPercent`, `xpRequiredForNextLevel`
- `currentRank(state)` from level (may differ from stored `progression.rank`)
- Quest lists filtered by status
- `isQuestOverdue`, `nextActionCue`
- Achievement locked/unlocked views

---

## Duplicate Sources of Truth (Documented — Do Not Fix Without Authorization)

| Concept | Sources | Risk |
|---------|---------|------|
| Rank | `progression.rank` stored vs `rankForLevel(level)` derived | Drift if JSON edited or load doesn't reconcile |
| Completion counts | `progression.totalCompleted` vs `daily.completedCount` | Different scopes (lifetime vs today) — intentional |
| Gold | `progression.gold` vs `daily.earnedGold` | Wallet vs daily earnings — intentional |
| Streak for achievements | `streak.current` OR `streak.best` | AchievementEngine checks both |

---

## Failure / Rollback Behavior

On `repository.saveState` failure:
1. `_state` is **not** updated
2. `DomainEvent.PersistenceFailed` emitted via EventBus
3. `CommandResult.Failure(PersistenceFailure)` returned

Verified by tests: `RewardEconomySystemTest`, `AchievementSystemTest`.

On validation failure:
- No state change, no persist, no events (except none emitted)

On idempotent success (empty events):
- `_state` not reassigned (unchanged reference)
- No persist

---

## Domain Events (All Types)

| Event | Typical Producer |
|-------|------------------|
| `ProfileCreated` | CreateProfile |
| `ProfileUpdated` | UpdateDisplayName |
| `ProfileReset` | ResetProfile |
| `StateLoaded` | LoadState |
| `QuestCreated/Started/Completed/Failed/Cancelled/Abandoned/Deleted` | Quest commands |
| `XpGained` | GainXp, CompleteQuest |
| `LevelUp` | GainXp, CompleteQuest |
| `RankPromoted` | GainXp, CompleteQuest |
| `DayStarted` | ResolveDailyCycle, CompleteQuest, LoadState (rollover) |
| `StreakStarted/Continued/Broken` | CompleteQuest, ResolveDailyCycle |
| `StreakChanged` | **Never emitted** (defined only) |
| `GoldEarned` | GainGold, CompleteQuest |
| `GoldPenaltyApplied` | FailQuest |
| `AchievementUnlocked` | Post-command achievement eval |
| `SettingsUpdated` | UpdateSettings |
| `PersistenceFailed` | Save failure |

**Subscribers:** `HunterViewModel.events` exposes EventBus flow; UI does not currently react to events (state-driven UI only).
