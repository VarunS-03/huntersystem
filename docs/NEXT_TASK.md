# Next Task

> Operational handoff for the next development session. **Do not execute implementation work described as out-of-scope.**

---

## TASK

**Step 8.0 — MVP Readiness / Productization Audit**

---

## OBJECTIVE

Systematically assess whether Hunter System is ready for MVP release. Identify gaps between current implementation and a shippable local-first Android app. Produce an auditable gap list and verify build/test health. **Audit only — no feature work.**

---

## WHY THIS IS NEXT

1. Steps 2–7 domain systems are implemented and covered by tests in source
2. Step 8.0 was partially started (UI diagnostic cards) but never completed
3. Permanent documentation now exists (`docs/`) — audit can reference it
4. No subsequent phase should begin until MVP readiness is assessed

---

## CURRENT PRECONDITIONS

| Precondition | Status |
|--------------|--------|
| Domain engines implemented | ✅ Verified in source |
| AppController transaction model | ✅ Verified |
| Persistence layer | ✅ Verified |
| Dashboard UI functional | ✅ Verified (single screen) |
| Unit/integration tests in source | ✅ 78 test methods |
| Documentation handoff | ✅ Created (this pass) |
| Tests executed | ❌ Not verified |
| Build executed | ❌ Not verified |
| Gradle wrapper in repo | ❌ Missing |

---

## WHAT MUST BE INSPECTED

### Build & Test
- Generate or restore Gradle wrapper if missing
- Run `./gradlew test --no-daemon`
- Run `./gradlew assembleDebug --no-daemon`
- Record pass/fail counts in `TEST_STATUS.md`

### MVP Gap Assessment
- `DashboardShellScreen.kt` — UI completeness vs developer scaffolding
- `MainActivity.kt`, `HunterViewModel.kt` — startup flow
- `Command.kt` — commands without UI (`UpdateSettings`, `AbandonQuest`, `GainXp`, `GainGold`)
- Legacy files: `com/example/ui/theme/`, `ExampleUnitTest.kt`, `ExampleRobolectricTest.kt`, `ExampleInstrumentedTest.kt`
- `metadata.json` vs actual capabilities
- `AndroidManifest.xml`, resources, themes
- Release signing configuration readiness

### Architecture Integrity (read-only)
- `AppController.kt` — transaction ordering unchanged
- State ownership per `ARCHITECTURE.md`
- Duplicate source-of-truth fields documented, not fixed

---

## EXPECTED OUTPUT

1. Updated `TEST_STATUS.md` with execution results (pass/fail, date, command)
2. MVP gap inventory document or section (can be appended to `CURRENT_PHASE.md` or separate audit file if authorized)
3. Prioritized list: must-fix vs nice-to-have for MVP
4. Confirmation that no application source was modified during audit

---

## WHAT MUST NOT BE IMPLEMENTED YET

Do **not** start without explicit authorization:

| Category | Examples |
|----------|----------|
| New domain features | Streak shields, shop, penalties on XP |
| Architecture refactors | Merging rank fields, splitting AppController |
| UI redesign | Multi-screen nav, onboarding, settings screen |
| New integrations | Gemini API, cloud sync |
| Bug fixes | Rank drift reconciliation, unless audit marks as MVP-blocker and user approves |
| Dead code removal | Legacy theme, example tests — audit first, remove only if authorized |
| Step 9+ features | Any phase beyond 8.0 |

---

## VERIFICATION CHECKLIST (for auditor)

- [ ] `./gradlew test` passes locally
- [ ] `./gradlew assembleDebug` succeeds
- [ ] All 78 tests accounted for in results
- [ ] MVP gap list written
- [ ] No Kotlin source modified
- [ ] Documentation updated with execution evidence

---

## AFTER STEP 8.0

Future phases (not authorized until audit completes and user approves):
- Remove/quarantine legacy boilerplate
- Add settings UI wired to `UpdateSettings`
- Add onboarding
- Compose interaction tests
- UI productization (remove dev diagnostics from user builds)
- Release preparation
