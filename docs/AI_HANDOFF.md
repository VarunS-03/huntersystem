# AI Handoff — Hunter System

> **THE REPOSITORY IS THE SOURCE OF TRUTH.**

> **NEVER TRUST A PREVIOUS AI REPORT WITHOUT VERIFYING IT AGAINST THE CODE.**

> **DO NOT REIMPLEMENT COMPLETED SYSTEMS.**

> **DO NOT CREATE DUPLICATE SOURCES OF TRUTH.**

> **DO NOT INTRODUCE FUTURE SYSTEMS WITHOUT AUTHORIZATION.**

> **DO NOT REFACTOR STABLE ARCHITECTURE WITHOUT A SPECIFIC VERIFIED PROBLEM.**

---

## AI Development Protocol

Before modifying code:

1. Read `MASTER_PROJECT_STATE.md`
2. Read `CURRENT_PHASE.md`
3. Read `NEXT_TASK.md`
4. Read `ARCHITECTURE.md`
5. Inspect relevant source files directly
6. Determine what already exists — do not assume gaps
7. State intended changes before implementing
8. Implement only authorized work
9. Run relevant tests (`./gradlew test`)
10. Update documentation to reflect changes
11. Report exactly what changed

---

## 1. What Hunter System Is

**Hunter System** is an Android app (`com.aistudio.huntersystem.qvrz`) inspired by Solo Leveling. It gamifies habits and tasks as "quests" with:

- **XP and levels** — exponential XP curve, rank promotions (E through S)
- **Gold economy** — earn from quests, lose from failures, never below zero
- **Daily cycles** — per-day counters that reset at midnight (local date)
- **Streaks** — consecutive days with qualifying activity (quest completion)
- **Achievements** — 7 milestone unlocks evaluated automatically

It is **local-first**: all state persists to a JSON file on device. No network calls in Kotlin source.

Default hunter name: "Sung Jinwoo" (Solo Leveling reference).

---

## 2. What Has Been Built

| Step | System | Status |
|------|--------|--------|
| 2 | Core architecture | ✅ Complete |
| 3 | Quest domain | ✅ Complete |
| 4 | Progression | ✅ Complete |
| 5 | Daily + Streak | ✅ Complete |
| 6 | Economy | ✅ Complete |
| 7 | Achievements | ✅ Complete |
| 8.0 | MVP audit | ⚠️ Partially started |

**78 tests** exist in source (77 unit + 1 instrumented). Execution not verified in doc environment.

---

## 3. Current Architecture

```
MainActivity
  → HunterViewModel (wires dependencies, dispatches init commands)
    → AppController (mutex, Command dispatch, StateFlow)
      → Domain Engines (pure calculations)
      → AchievementEngine (post-command eval)
      → JsonFileProfileRepository (Moshi JSON)
      → EventBus (fact notifications)
      → ClockPort / IdGenerator
    → AppState (immutable, single source of truth)
  → DashboardShellScreen (Compose UI, reads state, dispatches commands)
```

See `ARCHITECTURE.md` for full detail and Mermaid diagram.

---

## 4. AppState

Single root object containing everything:

```kotlin
AppState(
  schemaVersion, profile, progression, quests, streak,
  daily, achievements, settings, metadata
)
```

- **Immutable** — all changes via `.copy()`
- **Persisted whole** — one JSON file
- **Observed by UI** — via `StateFlow` from AppController through ViewModel

Key nested models: `Quest`, `Progression`, `DailyRecord`, `Streak`, `Achievements`, `Settings`, `Metadata`, `QuestsState`.

---

## 5. AppController

**The only place that mutates application state.**

```kotlin
suspend fun dispatch(command: Command): CommandResult
```

- Guarded by `Mutex` (one transaction at a time)
- Reads clock once per transaction
- Validates input
- Calls pure engines
- Builds candidate state
- Evaluates achievements
- **Saves to disk first**
- Then updates memory
- Then emits events

**Never bypass AppController** to write state, repository, or events.

---

## 6. Domain Engines

| Engine | Pure Functions | Called By |
|--------|----------------|-----------|
| `ProgressionEngine` | XP, level, rank calculations | GainXp, CompleteQuest, FailQuest |
| `RewardEngine` | Gold reward/penalty/gain | CompleteQuest, FailQuest, GainGold |
| `DailyEngine` | Day rollover, daily counters | CompleteQuest, FailQuest, ResolveDailyCycle, LoadState |
| `StreakEngine` | Streak qualification/break | CompleteQuest, ResolveDailyCycle, LoadState |
| `AchievementEngine` | Unlock evaluation | AppController post-command fold |

Engines take data in, return data out. **No I/O. No events. No state mutation.**

---

## 7. State Ownership

| Domain | Authoritative Fields | Derived (via Selectors) |
|--------|---------------------|-------------------------|
| Progression | xp, level, gold, rank, totalCompleted, totalFailed | xpProgressPercent, currentRank(level) |
| Daily | activeDate, counters, earnedXp/Gold | — |
| Streak | current, best, lastActivityDate | isStreakAtRisk, etc. |
| Quests | byId, order | activeQuests, pendingQuests, etc. |
| Achievements | unlockedIds, unlockedTimestamps | locked/unlocked lists |
| Economy | **progression.gold** (wallet) | daily.earnedGold (today only) |

### Duplicate Sources (DO NOT "FIX" WITHOUT AUTHORIZATION)

- `progression.rank` stored AND derivable from level
- Lifetime vs daily completion counts (intentional different scopes)
- `shieldCount` in Streak model — unused, reserved

---

## 8. Transaction Model

```
Command → validate → engines → candidate AppState
  → AchievementEngine.evaluate
  → if events non-empty:
       saveState → (fail? rollback + PersistenceFailed)
       _state = finalState
       eventBus.emitAll
  → CommandResult
```

**Exceptions:**
- Empty events → no save, no memory update (idempotent)
- LoadState → always saves (emits StateLoaded)
- Persistence failure → memory unchanged

CompleteQuest is the most complex transaction — touches quest, progression, daily, streak, achievements, and emits up to 8 event types.

---

## 9. Event Model

**Events are FACTS, not commands.**

Defined in `DomainEvent.kt` sealed interface. Published by AppController after successful persistence.

UI currently **does not react to events** — it observes `StateFlow<AppState>` only. EventBus is available via `HunterViewModel.events` for future use (toasts, analytics, sound).

`PersistenceFailed` is the only event emitted on failure path.

Dead code: `StreakChanged` event type is defined but never emitted.

---

## 10. Persistence

- **File:** `{filesDir}/hunter_system_profile_v1.json`
- **Library:** Moshi with Kotlin reflection adapter
- **Write:** temp file → backup current → rename temp to target
- **Load failure:** try `.bak` → else create default
- **Interface:** `ProfileRepository` (5 methods)

Repository is injected into AppController. ViewModel creates `JsonFileProfileRepository(application.filesDir)` in factory.

---

## 11. UI

**One screen:** `DashboardShellScreen`

User interactions:
- Create quest (title, description, tier E/N/H/S)
- Filter quests (ALL/ACTIVE/PENDING/COMPLETED)
- Start, Complete, Fail, Cancel, Delete quests
- View achievements, stats, diagnostics
- Reset profile / update name (dev controls)

**Not in UI:** settings, onboarding, navigation, due dates, GainXp/GainGold manual commands, AbandonQuest.

Theme: `HunterSystemTheme` (dark, neon cyan/gold). Legacy `com.example.ui.theme` is unused.

---

## 12. Test State

| Metric | Value |
|--------|-------|
| Total tests | 78 |
| Project-specific | 75 |
| Boilerplate | 3 |
| Executed in doc env | 0 |

Test patterns:
- `FakeProfileRepository` for integration tests
- `TestClockPort` for date-sensitive tests
- `DeterministicIdGenerator` for predictable IDs

Run: `./gradlew test` (wrapper may need generation first).

See `TEST_STATUS.md` for full file breakdown.

---

## 13. Current Phase

**Step 8.0 — MVP Readiness / Productization Audit** (partially started)

Steps 2–7 are complete. Step 8 has UI diagnostic scaffolding but no formal audit execution.

---

## 14. Current Unfinished Work

- MVP audit execution (build/test verification, gap inventory)
- Settings UI (command exists)
- Navigation / onboarding
- UI productization (remove dev diagnostics for release)
- Legacy cleanup (`com.example.ui.theme`, example tests)
- Gradle wrapper scripts missing from repo
- Compose interaction tests
- `shieldCount` streak shields (model only)
- `metadata.json` Gemini API reference (no code)

---

## 15. Immediate Next Task

**Step 8.0 MVP Readiness Audit** — see `NEXT_TASK.md`

Audit only. Do not implement features. Do not refactor.

First actions:
1. Generate/restore Gradle wrapper
2. Run `./gradlew test assembleDebug`
3. Record results in `TEST_STATUS.md`
4. Produce MVP gap list

---

## 16. Known Risks

1. Tests unverified by execution
2. No Gradle wrapper in repo
3. Rank field may drift from level
4. Legacy code may confuse agents
5. Dev-oriented UI not production-polished
6. LoadState rollover doesn't bump revision

---

## 17. Important Constraints

### DO change (when authorized)
- Add features per approved phase
- Add tests for new behavior
- Update docs after changes
- Fix bugs marked MVP-blockers after audit approval

### DO NOT change (without explicit authorization)
- AppController transaction ordering (persist before commit)
- Engine purity (no I/O in engines)
- AppState as single source of truth
- EventBus fact-only semantics
- Duplicate source-of-truth fields (rank, daily vs lifetime counts)
- Architecture layer boundaries

### DO NOT reimplement
- Quest lifecycle (complete)
- Progression/XP (complete)
- Daily/streak (complete)
- Economy (complete)
- Achievements (complete)
- JSON persistence (complete)

### Files to read before any domain work

| Task | Read First |
|------|------------|
| Any mutation | `AppController.kt`, `Command.kt` |
| Quest changes | `Quest.kt`, `QuestDomainTest.kt` |
| XP/level | `ProgressionEngine.kt`, `ProgressionSystemTest.kt` |
| Daily/streak | `DailyEngine.kt`, `StreakEngine.kt` |
| Gold | `RewardEngine.kt`, `RewardEconomySystemTest.kt` |
| Achievements | `AchievementEngine.kt`, `AchievementDefinition.kt` |
| UI changes | `DashboardShellScreen.kt`, `HunterViewModel.kt` |
| Persistence | `JsonFileProfileRepository.kt`, `ProfileRepositoryTest.kt` |

---

## Quick Reference: Commands

```kotlin
LoadState, ResetProfile, CreateProfile, UpdateDisplayName, UpdateSettings,
ResolveDailyCycle, GainXp, GainGold,
CreateQuest, StartQuest, CompleteQuest, FailQuest, CancelQuest, AbandonQuest, DeleteQuest
```

---

## Quick Reference: Key Paths

```
app/src/main/java/com/example/huntersystem/
  application/AppController.kt      ← START HERE for behavior
  state/AppState.kt                 ← START HERE for data shape
  domain/engine/*.kt                ← Pure logic
  presentation/screens/DashboardShellScreen.kt  ← UI
  data/repository/JsonFileProfileRepository.kt    ← Persistence

app/src/test/java/com/example/huntersystem/     ← Tests
docs/                                            ← This handoff layer
```

---

*This document was created from repository inspection. Verify against code before relying on any claim.*
