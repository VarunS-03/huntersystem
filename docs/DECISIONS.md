# Architectural Decisions

> Decisions inferred from code structure and behavior. Reasons marked **INFERRED** where not explicitly documented in source comments.

---

## 1. AppState as Single Source of Truth

**DECISION:** All application data lives in one immutable `AppState` object held by `AppController`.

**EVIDENCE:** `AppController._state: MutableStateFlow<AppState>`; all commands produce new state via `.copy()`; `JsonFileProfileRepository` serializes entire `AppState`.

**REASON:** Simplifies persistence (one JSON document), enables atomic transactions, makes state replayable. **(INFERRED)**

**CONSEQUENCE:** Any new domain data must be added to `AppState` or a nested model within it. No parallel state stores.

---

## 2. Immutable State Transitions

**DECISION:** State is never mutated in place. All transitions use Kotlin `data class.copy()`.

**EVIDENCE:** `AppController` handlers; `AppStateTest.testImmutabilityViaCopy`; engines return new objects.

**REASON:** Thread safety, predictable diffs, easier testing. **(INFERRED)**

**CONSEQUENCE:** Large state copies on every command (acceptable at current scale).

---

## 3. AppController as Sole Orchestrator

**DECISION:** All mutations flow through `AppController.dispatch(Command)`. No direct repository or state manipulation from UI.

**EVIDENCE:** `HunterViewModel.dispatch` → `controller.dispatch`; UI receives `onDispatch: (Command) -> Unit`.

**REASON:** Centralized validation, ordering, and side effects. **(INFERRED)**

**CONSEQUENCE:** Cross-domain orchestration (e.g., CompleteQuest) lives in AppController, not in engines.

---

## 4. Pure Domain Engines

**DECISION:** Domain engines are stateless `object`s with pure functions — no I/O, no side effects.

**EVIDENCE:** `ProgressionEngine`, `RewardEngine`, `DailyEngine`, `StreakEngine`, `AchievementEngine` — all take inputs, return results.

**REASON:** Testability, determinism, separation of calculation from orchestration. **(INFERRED)**

**CONSEQUENCE:** Engines cannot persist or emit events. AppController must call them and apply results.

---

## 5. EventBus as Fact Notification (Not Commands)

**DECISION:** Events describe what happened. Subscribers do not trigger state changes.

**EVIDENCE:** `EventBus.emit` after persistence; no listener calls `AppController.dispatch`; UI driven by `StateFlow`, not events.

**REASON:** Unidirectional data flow; events for logging, analytics, future UI effects. **(INFERRED)**

**CONSEQUENCE:** `StreakChanged` event type exists but is unused — likely superseded by Started/Continued/Broken events.

---

## 6. Persistence Before Memory Commit and Event Publication

**DECISION:** `repository.saveState()` must succeed before `_state.value` is updated and events are emitted.

**EVIDENCE:** `AppController.dispatch` fold block lines 653–668; tests verify rollback on failure.

**REASON:** Prevent in-memory/disk divergence; crash safety. **(INFERRED)**

**CONSEQUENCE:** Failed saves return `PersistenceFailure` and leave state unchanged. `PersistenceFailed` event is the exception (emitted on failure).

---

## 7. ClockPort for Time Abstraction

**DECISION:** Application layer uses `ClockPort` interface; production uses `SystemClockPort`; tests use `TestClockPort`.

**EVIDENCE:** `AppController` constructor takes `ClockPort`; all timestamps/dates from clock in dispatch.

**REASON:** Deterministic tests for daily rollover, streak, and timestamps. **(INFERRED)**

**CONSEQUENCE:** `StreakEngine.calculateDaysBetween` uses `LocalDate.parse` directly (date math exception, acceptable).

---

## 8. Selectors for Derived Read Values

**DECISION:** UI and tests read computed values through `Selectors` object, not inline calculations.

**EVIDENCE:** `DashboardShellScreen` uses `Selectors.xpProgressPercent`, `currentGold`, `nextActionCue`, etc.

**REASON:** Single place for read logic; keeps UI thin. **(INFERRED)**

**CONSEQUENCE:** Some duplication with engine functions (e.g., `rankForLevel` in both engine and selectors).

---

## 9. Domain Boundaries via Command Handlers

**DECISION:** Each domain's mutations are triggered by named commands, not cross-engine calls.

**EVIDENCE:** `GainXp`, `GainGold`, `ResolveDailyCycle` separate from quest commands; CompleteQuest orchestrates multiple engines in one handler.

**REASON:** Explicit API surface; testable command paths. **(INFERRED)**

**CONSEQUENCE:** CompleteQuest is the most complex handler — intentional cross-domain transaction.

---

## 10. Atomic Transaction via Mutex

**DECISION:** One command at a time via `transactionMutex.withLock`.

**EVIDENCE:** `AppController.dispatch` wraps entire handler in mutex.

**REASON:** Prevent concurrent dispatch corrupting state. **(INFERRED)**

**CONSEQUENCE:** Long-running commands block others (negligible at current scale).

---

## 11. Achievement Evaluation After Every Successful Command

**DECISION:** `AchievementEngine.evaluate` runs on candidate state after command logic, before persist.

**EVIDENCE:** AppController fold block; not inside individual command handlers.

**REASON:** Achievements are cross-cutting concerns based on final state snapshot. **(INFERRED)**

**CONSEQUENCE:** Any command that changes qualifying fields can unlock achievements atomically with that command's persist.

---

## 12. Idempotent Commands Skip Persistence When No Events

**DECISION:** If `allEvents` is empty after command + achievement eval, no save and no memory update.

**EVIDENCE:** `if (allEvents.isNotEmpty()) { save... }` in AppController.

**REASON:** Avoid unnecessary disk writes for no-op operations. **(INFERRED)**

**CONSEQUENCE:** `ResolveDailyCycle` on same day and `StartQuest` on already-active quest don't touch disk.

**Exception:** `LoadState` always emits `StateLoaded` → always persists.

---

## 13. Local-First JSON Persistence

**DECISION:** Single JSON file in app internal storage; no database, no network.

**EVIDENCE:** `JsonFileProfileRepository`; comment in build.gradle: `// Minimal, local-first MVP dependencies`.

**REASON:** Simplicity for MVP; offline-first. **(INFERRED from comment + implementation)**

**CONSEQUENCE:** Full state rewrite on each save; schema migration not yet implemented beyond `schemaVersion` field.

---

## 14. Gold Stored in Progression (Not Separate Wallet)

**DECISION:** Authoritative gold balance is `AppState.progression.gold`.

**EVIDENCE:** `RewardEngine` reads/writes via progression; `Selectors.currentGold` reads progression.

**REASON:** Progression and economy unified in hunter stats model. **(INFERRED)**

**CONSEQUENCE:** `daily.earnedGold` is a separate daily counter — intentional dual tracking.

---

## 15. Rank Stored and Computed

**DECISION:** Rank is persisted in `progression.rank` AND computable via `ProgressionEngine.rankForLevel(level)`.

**EVIDENCE:** `applyXpGain` updates both level and rank; `Selectors.currentRank(level)` calls engine.

**REASON:** Fast read without computation; rank promotion events. **(INFERRED)**

**CONSEQUENCE:** Potential drift if state loaded with inconsistent rank/level — not reconciled on LoadState.

---

## 16. Quest Collection: Map + Order List

**DECISION:** Quests stored as `byId: Map<String, Quest>` plus `order: List<String>` for display sequence.

**EVIDENCE:** `QuestsState` in AppState; `Selectors.allQuests` maps order to quests.

**REASON:** O(1) lookup by ID; stable display order. **(INFERRED)**

**CONSEQUENCE:** Delete must update both map and order list.

---

## 17. Compose Single-Activity Architecture

**DECISION:** One activity, one screen, state hoisted from ViewModel.

**EVIDENCE:** `MainActivity` → `DashboardShellScreen`; no NavHost.

**REASON:** MVP scope; developer verification dashboard. **(INFERRED)**

**CONSEQUENCE:** No navigation graph; all features on one scrollable screen.

---

## 18. Deterministic ID Generation in Tests

**DECISION:** Tests use `DeterministicIdGenerator`; production uses `UuidGenerator`.

**EVIDENCE:** Test setup in QuestDomainTest, DailyStreakSystemTest, etc.

**REASON:** Predictable IDs for assertions. **(INFERRED)**

**CONSEQUENCE:** ID format differs between test and production (`quest_000001` vs `quest_a1b2c3d4e5f6`).

---

## 19. CancelQuest vs AbandonQuest

**DECISION:** Two commands with identical behavior but different event types.

**EVIDENCE:** Shared handler branch in AppController; `QuestCancelled` vs `QuestAbandoned` events.

**REASON:** Semantic distinction for future UX (user cancel vs system abandon). **(INFERRED)**

**CONSEQUENCE:** UI only exposes Cancel; Abandon unused.

---

## 20. Settings Model Without UI

**DECISION:** `Settings` data class and `UpdateSettings` command exist; no UI wiring.

**EVIDENCE:** `Settings.kt`, `Command.UpdateSettings`, no settings composable.

**REASON:** Prepared for future settings screen. **(INFERRED)**

**CONSEQUENCE:** Settings always persist defaults unless command dispatched programmatically.
