# Test Status

> Distinguishes tests **verified by execution** from tests **present in source but not executed** in the documentation environment.

---

## Execution Summary

| Check | Status | Evidence |
|-------|--------|----------|
| Unit tests executed | **NOT EXECUTED** | No `gradlew`/`gradlew.bat` in repository; Gradle could not run |
| Instrumented tests executed | **NOT EXECUTED** | Same |
| Debug build executed | **NOT EXECUTED** | Same |
| Recommended command | `./gradlew test assembleDebug --no-daemon` | Requires Gradle wrapper generation or system Gradle |

**Action required:** Run tests locally and update this file with date, command, and pass/fail results.

---

## Test Counts (from source `@Test` annotations)

| Category | Count |
|----------|-------|
| Unit tests (`app/src/test`) | **77** |
| Instrumented tests (`app/src/androidTest`) | **1** |
| **Total** | **78** |

### By file

| File | Tests | Category |
|------|-------|----------|
| `QuestDomainTest.kt` | 10 | Quest domain + AppController |
| `ProgressionSystemTest.kt` | 14 | Progression engine + commands |
| `DailyStreakSystemTest.kt` | 13 | Daily + Streak engines + integration |
| `RewardEconomySystemTest.kt` | 12 | Economy engine + integration |
| `AchievementSystemTest.kt` | 9 | Achievement engine + integration |
| `ProfileRepositoryTest.kt` | 4 | Persistence |
| `CommandPipelineTest.kt` | 3 | Application pipeline |
| `AppStateTest.kt` | 3 | State + selectors |
| `InfrastructurePortsTest.kt` | 4 | Clock + ID generation |
| `EventBusTest.kt` | 2 | Event transport |
| `GreetingScreenshotTest.kt` | 1 | UI screenshot (Roborazzi) |
| `ExampleUnitTest.kt` | 1 | **Boilerplate** (template) |
| `ExampleRobolectricTest.kt` | 1 | **Boilerplate** (template) |
| `ExampleInstrumentedTest.kt` | 1 | **Boilerplate** (template) |

**Project-specific tests (excluding 3 boilerplate):** 75  
**Boilerplate tests:** 3

---

## Test Categories

### Domain Unit Tests — EXISTS, NOT EXECUTED

| Domain | File | Coverage |
|--------|------|----------|
| Quest lifecycle | `QuestDomainTest.kt` | Create, start, complete, fail, cancel, delete, validation |
| Progression | `ProgressionSystemTest.kt` | XP curve, level-up, rank promotion, GainXp |
| Daily | `DailyStreakSystemTest.kt` | resolveDay, rollover, counters |
| Streak | `DailyStreakSystemTest.kt` | qualification, continuation, break, rollover |
| Economy | `RewardEconomySystemTest.kt` | reward, penalty, direct gain, selectors |
| Achievements | `AchievementSystemTest.kt` | catalog, pure engine, idempotency |

### Integration Tests — EXISTS, NOT EXECUTED

| Test | File | What it verifies |
|------|------|------------------|
| CompleteQuest full pipeline | `RewardEconomySystemTest.kt` | Quest + XP + gold + daily + streak + events |
| Multi-day gold rollover | `RewardEconomySystemTest.kt` | Wallet persists, daily earnedGold resets |
| Achievement unlock on complete | `AchievementSystemTest.kt` | Atomic unlock + persist + events |
| Multi-achievement persistence roundtrip | `AchievementSystemTest.kt` | Save/load preserves unlocks |
| Daily/streak via CompleteQuest | `DailyStreakSystemTest.kt` | Cross-day quest completion |
| ResolveDailyCycle | `DailyStreakSystemTest.kt` | Rollover command |
| Profile commands | `CommandPipelineTest.kt` | UpdateDisplayName, ResetProfile |

### Persistence Tests — EXISTS, NOT EXECUTED

| Test | File |
|------|------|
| Save/load roundtrip | `ProfileRepositoryTest.kt` |
| Backup recovery | `ProfileRepositoryTest.kt` |
| Export/import | `ProfileRepositoryTest.kt` |
| Corrupt file recovery | `ProfileRepositoryTest.kt` |
| Persistence failure rollback | `RewardEconomySystemTest.kt`, `AchievementSystemTest.kt` |

### Failure / Rollback Tests — EXISTS, NOT EXECUTED

| Test | File | Behavior verified |
|------|------|-------------------|
| Persistence failure prevents mutation | `RewardEconomySystemTest.kt` | Gold/XP/quest status unchanged on save fail |
| Achievement unlock aborted on save fail | `AchievementSystemTest.kt` | No unlock in memory, no AchievementUnlocked event |
| Empty display name validation | `CommandPipelineTest.kt` | InvalidInput, no state change |
| Terminal quest re-complete | `QuestDomainTest.kt` | InvalidStateTransition |
| Duplicate complete no double gold | `RewardEconomySystemTest.kt` | Idempotent terminal state |

### Clock / Date Tests — EXISTS, NOT EXECUTED

| Test | File | Mechanism |
|------|------|-----------|
| Daily rollover | `DailyStreakSystemTest.kt` | `TestClockPort.setTime` / `advanceTime` |
| Streak consecutive days | `DailyStreakSystemTest.kt` | Date advancement |
| Multi-day economy | `RewardEconomySystemTest.kt` | Clock date changes |
| Infrastructure | `InfrastructurePortsTest.kt` | TestClockPort fixed values |

### UI / Robolectric Tests — EXISTS, NOT EXECUTED

| Test | File | Notes |
|------|------|-------|
| Dashboard screenshot | `GreetingScreenshotTest.kt` | Static `AppState.createDefault()`, no interactions |
| Example Robolectric | `ExampleRobolectricTest.kt` | Template boilerplate |
| Example instrumented | `ExampleInstrumentedTest.kt` | Package name check only |

**Gap:** No Compose UI interaction tests (button clicks, quest creation flow).

---

## Test Infrastructure

| Component | Usage |
|-----------|-------|
| JUnit 4 | All unit tests |
| `kotlinx.coroutines.test.runTest` | Async command tests |
| `FakeProfileRepository` | In-memory repo in integration tests |
| `TestClockPort` | Deterministic dates |
| `DeterministicIdGenerator` | Predictable IDs |
| Robolectric + Roborazzi | Screenshot test |
| `createComposeRule` | Compose UI test rule |

---

## Build Evidence

| Item | Status |
|------|--------|
| `app/build.gradle.kts` | Present — Compose, Moshi, coroutines, test deps |
| `gradle/libs.versions.toml` | Present — AGP 8.8.2, Kotlin 2.0.21 |
| `gradle/wrapper/gradle-wrapper.properties` | Present |
| `gradlew` / `gradlew.bat` | **MISSING** |
| Roborazzi plugin | Configured |
| Unit tests include Android resources | `testOptions.unitTests.isIncludeAndroidResources = true` |

---

## Verification Protocol (for next session)

```bash
# If wrapper missing, generate first:
gradle wrapper

# Then run:
./gradlew test --no-daemon
./gradlew assembleDebug --no-daemon
```

Record in this file:
- Date of execution
- Pass/fail counts
- Any failing test names
- APK output path if build succeeds

---

## VERIFIED BY EXECUTION

**None** in the documentation creation environment.

---

## EXISTS BUT NOT EXECUTED

All 78 test methods across 14 test files.
