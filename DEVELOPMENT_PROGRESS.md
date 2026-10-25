# RalLedger v2.0 Development Progress

Updated: 2026-10-24
Branch: `feature/ralledger-v2-live-ready` (schema-22 restore-recovery release published to `main`)
Baseline: `3cdb31c1d18fe1dace524ad6431c97686ec21faf`

## Delivered in this checkpoint

- Verified the original clean `master` baseline and created the requested feature branch. At that initial checkpoint no remote was configured; later publishing checkpoints are recorded below.
- Added root ignore rules for local Android configuration, generated builds, signing material, local JDK files, databases, and financial exports.
- Replaced permissive floating-point input parsing in production screens with decimal-safe centavo parsing and visible validation.
- Made account opening balance + journal posting atomic; opening balances are shown without an expense sign.
- Made income, expense, transfer, receivable recognition/collection, subscription writes, and reconciliation adjustments atomic and validate account/category targets.
- Changed journal posting to abort on duplicate row IDs; journal entry + lines are inserted in one Room transaction.
- Corrected liability reconciliation normal-side handling and added positive/negative tests for asset and liability adjustments.
- Added transaction reversal entries with preserved audit history. Reversing a collection restores the receivable balance and marks the payment reversed atomically. Reversal and payment relationships are migrated and indexed.
- Receivable creation now recognizes income once through a balanced journal entry. Collection checks positive amounts, remaining balance, collectible status, and active liquid destination accounts under a database transaction. Overdue status is derived from due dates in the live flow.
- Added encrypted SAF backup save/open flows using AES-256-GCM with a random nonce and PBKDF2-HMAC-SHA256 password derivation. At this checkpoint backup coverage includes accounts, categories, budgets, expected income, bills/loans and repayment history, journal entries/lines, receivables/payments/write-offs, subscriptions, and reconciliations. Restore previews counts and integrity errors, then replaces current rows atomically with a post-restore equivalence check.
- Disabled Android automatic data backup and excluded app data in both legacy and Android 12+ extraction rules; the portable in-app backup remains the supported recovery path.
- Corrected dashboard asset aggregation and moved it into a unit-testable calculator. Reversal postings are included as contra income/expense on the reversal date. Subscription commitments are normalized to monthly amounts.
- Added synthetic instrumentation coverage for concurrent receivable collection, payment reversal, and encrypted backup/restore equivalence. It compiled at the initial checkpoint; the schema-8 connected suite later passed the backup/concurrency coverage.
- Added custom unit tests for centavo parsing, reconciliation, journal reversals, monthly subscription conversion, dashboard totals, and reversal effects on current-month totals.
- Applied a cohesive visual pass to the existing app: saved System/Light/Dark appearance plus Ocean/Indigo/Sage palettes; rounded shared surfaces; a money-first dashboard; numeric-first transaction entry; and a month-switchable, tappable category-spending chart.
- Scoped the app-wide transaction FAB to Home and Insights so it no longer overlaps the account, transaction, receivable, or subscription screens' own primary actions.
- Simplified the dashboard header to a single `Overview` label and removed the redundant tagline and supporting sentence.
- Removed the marked extra dashboard section heading/captions and the Insights tagline while keeping the money summaries.
- Insights now total only income and expense activity for the selected month, omit reversed entries from the original period, and show category shares and normalized subscription commitments.
- Expanded account management into a complete safe lifecycle slice: search, optional archived-account view, edit of name/institution/description/icon/color/dashboard inclusion, per-account transaction history, archive/restore, and confirmed delete-if-unused.
- Account edits now update metadata only; account type, currency, opening balance, ledger balance, creation time and reconciliation metadata are protected from accidental edits.
- Permanent account deletion checks journal entries/lines, reconciliation records, receivable-payment destinations, and subscription links atomically. Used accounts remain available for archive; system accounts are protected.
- Added Room schema migration 2→3 for account descriptions and included descriptions in encrypted backups. Schema-2 encrypted backups remain readable with an empty description.
- Added `FEATURE_CRUD_MATRIX.md` to distinguish working lifecycle controls from create-only or repository-only functionality, and documented the reported MyMoney permission without assuming rights to specific assets or source code.
- Added emulator instrumentation for account metadata persistence, unchanged ledger/journal values, archive/restore, safe deletion, and refusal to delete an account with journal history.
- Added category management for income and expenses: search, archived view, name/icon/color/parent edits, one-level subcategories, history, archive/restore, same-type merge, and delete-if-unused.
- Income sources are user-managed INCOME categories. Category merge atomically reassigns journal headers/category ledger lines and reparents children while preserving amounts, account associations and original journal rows.
- Transaction and receivable entry controls now exclude archived categories; repository validation rejects an archived category even if a stale selector submits it.
- Raised Room schema to version 4 for category hierarchy/archive state and added encrypted-backup serialization and validation for the new fields.
- Emulator coverage includes category edit/archive/restore/merge/history safety and migration from a schema-2 database through schema 4.
- Moved the quick transaction shortcut into the top app bar on Home and Insights after launch inspection showed the floating button covered the monthly Net flow value.
- Changed the dashboard’s primary card to Available Money using persisted liquid cash and bank balances, with Net Worth displayed separately and assets/liabilities still visible.
- Removed the redundant monthly cash-flow caption and moved Add category into the category screen header so the fixed action no longer covers category overflow menus.
- Fixed a centavo-unit mismatch in the existing concurrency/backup instrumentation assertion; the test had searched for 400,000 centavos after posting 4,000.
- Added transaction correction for income, expense, and transfer entries. Corrections post a reversing journal entry and a replacement atomically; the original remains unchanged and is labeled Reversed. Linked receivable transactions and already-reversed entries are rejected before mutation.
- Added transaction duplication with a prefilled entry form, plus type, account, category, and date-range filters. Search now also matches counterparty and either side of a transfer.
- Added row actions for Correct, Duplicate, and Void with reversal. Reversed originals have no further actions; correction preserves the transaction type and exposes the relevant editable fields.
- Added unit coverage for transaction search/filter behavior and emulator coverage for atomic correction, preserved original journal lines, rejected invalid targets, and receivable safety.
- Added a schema-1 migration fixture populated across accounts, categories, journal entries/lines, receivables/payments, subscriptions and reconciliations. It verifies all records survive the 1→2→3→4 upgrade and that newly introduced fields get safe defaults.
- Added monthly category budgets with create, edit, archive/restore, and confirmed delete controls in Planning. Actual spending is calculated from persisted non-reversed expenses and transfer fees; reversed entries do not count. Budget/category merge collisions are rejected atomically, while non-colliding category merges preserve budget links.
- Raised Room to schema 5 with a category foreign key and unique category/month budget constraint. Encrypted backup/restore now includes budget records and validates category, period, limit, and duplicate integrity.
- Expanded subscription management with editable billing details and next date, pause/resume/cancel/reactivate, archive/restore, and confirmed delete. Editing preserves status and payment metadata; subscriptions with a recorded last-payment date cannot be deleted. Archived schedules no longer contribute to monthly commitments.
- Raised Room to schema 6 with a default-false subscription archive field and added the 5→6 migration. Encrypted backup/restore carries that field and older backups default it to false.
- Added instrumented coverage for subscription metadata preservation, status transitions, archive guards, safe deletion, populated schema-1→6 migration, and archived-subscription backup round-trip. It compiled at this checkpoint; later schema-8 connected verification is recorded below.
- Completed receivable lifecycle controls: edit client/description/due date without rewriting posted financial history; inspect collection and write-off history; reverse individual history entries; cancel by reversing recognition after active collection/write-offs are reversed; write off partial or full balances to a selected expense category; archive/restore only closed records.
- Receivable write-offs now post a balanced expense-versus-receivable journal entry, reduce receivables once, affect expense charts/budgets, and can be reversed. Schema 7 adds receivable archive state and a write-off history table with foreign keys; encrypted backup validates and restores write-offs and archived state, while earlier backups default safely.
- Added instrumented receivable lifecycle, schema-1→7 migration, and archived write-off backup round-trip coverage; it compiled at this checkpoint. The later schema-8 connected suite verified these flows on an emulator.
- Added expected-income planning with create, edit, search, cancel/reactivate, archive/restore, and guarded delete. Converting a forecast atomically creates one recognized receivable and links it back to the forecast; duplicate conversion and deletion after conversion are blocked.
- Raised Room schema to version 8 for expected-income records with foreign keys to the selected income category and, after conversion, the resulting receivable. Category merge reassigns forecasts, their linked receivables, journal categories and write-off categories; category deletion is blocked while forecasts refer to it. Encrypted backup validates/restores the forecast-to-receivable relationship, and older backups default to no expected-income records.
- Added instrumentation coverage for expected-income CRUD, category merge/reference protection, single conversion and journal balance, and encrypted backup round-trip; schema-1/schema-2 fixtures now migrate through schema 8.

## Verification

- Environment: JDK 25.0.4.1 Temurin was downloaded from the official Adoptium API, SHA-256 checked against its official metadata, and placed in ignored `.ralledger-local/jdk25` so no project Java/Gradle versions needed changing. The installed SDK already includes API 35/37, build-tools 36.0.0, platform-tools, emulator, and an API 37 x86_64 system image.
- Passed: `:app:assembleDebug` after account and category CRUD/schema changes.
- Passed after the visual redesign: fresh `:app:assembleDebug --rerun-tasks` (all 38 tasks executed; no test tasks run in this UI pass).
- Passed after the FAB overlap fix: `:app:assembleDebug` (incremental debug rebuild).
- Passed after trimming dashboard copy: `:app:assembleDebug`.
- Passed after removing the marked dashboard and Insights copy: `:app:assembleDebug`.
- Passed: `:app:testDebugUnitTest` — 14 tests, 0 failures, 0 errors.
- Passed: `:app:lintDebug` — task succeeds with 23 guidance warnings, mostly dependency/API recommendations; no lint errors.
- Passed: `:app:compileDebugAndroidTestKotlin` — the new database/backup instrumentation test source compiles.
- Passed: `:app:compileDebugAndroidTestKotlin` including the new account lifecycle coverage.
- Passed: `:app:connectedDebugAndroidTest` on `Medium_Phone(AVD) - 17` — 5 tests, 0 failures, including schema-2→4 migration, account/category lifecycle, income-category merge, backup round-trip with new metadata, and receivable reversal/concurrency.
- Passed: final combined debug build, 14 unit tests, lint, Android-test Kotlin compilation, and 5 connected emulator tests after the dashboard and category-action layout updates; lint reports 23 issues, with no task failure.
- Passed: final combined `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`, and `:app:connectedDebugAndroidTest` after transaction correction/filter and schema-1 fixture work. Results: 18 unit tests and 8 connected emulator tests, all passing; lint task succeeds with 23 guidance issues and no task failure.
- Passed: schema-1 fixture migration preserves account, category, journal, receivable/payment, subscription and reconciliation data through schema 4; schema-2 fixture migration preserves account/category data and adds blank description, no parent and active category status.
- Passed after the budget module: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`. Results: 19 unit tests, 0 failures; lint succeeds with 23 warnings and 0 errors; Android instrumentation sources, including schema-1→5, budget CRUD/merge, and budget backup coverage, compile.
- Passed after subscription changes: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`. Results: 20 unit tests, 0 failures/errors; lint succeeds with 23 warnings and 0 errors; Android-test sources for schema-1→6 migration, subscription lifecycle, and archived-subscription backup compile.
- At the schema-6 checkpoint, the clean headless `Medium_Phone` launch remained offline, so the new subscription tests had not yet run. The later schema-8 connected suite passed subscription lifecycle and encrypted backup coverage.
- Passed after receivable lifecycle changes: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:compileDebugAndroidTestKotlin`. Results: 21 unit tests, 0 failures/errors; lint has 23 issues and 0 errors; the schema-1→7 migration, receivable lifecycle, and write-off backup instrumentation sources compile.
- At the schema-7 and budget checkpoints, ADB reported the emulator offline. The later schema-8 connected suite passed receivable and budget lifecycle tests, encrypted backup round-trip, and the populated schema-1/schema-2 migration chains through schema 8.
- Passed: debug APK launch smoke check on `emulator-5554`; Overview showed Available Money first with Net Worth separate, the transaction shortcut clear of the monthly summary, the Accounts empty state, and Categories opened from Settings. The category list and Add category dialog were visually checked; full account/category form submission and lifecycle actions remain outstanding. One offline emulator was skipped.
- Passed: transaction UI smoke on `emulator-5554` using only synthetic data. The journal filter panel rendered; Duplicate opened with account/category/amount/description/date prefilled; Correct opened with editable fields; saving a correction displayed the reversal, a Reversed marker on the original, and the replacement entry. The synthetic correction was posted only to the emulator test install.
- Passed: schema-8 UI smoke on `Medium_Phone(AVD) - 17`. Planning → Expected income rendered; the add and edit forms opened; a synthetic forecast was created, cancelled, and deleted through the UI; the list returned to zero records. The synthetic record was removed from the emulator afterward.
- Passed prior combined verification on `Medium_Phone(AVD) - 17`: 21 unit tests and 13 connected instrumentation tests; schema-1→8 and schema-2→8 migrations, account/category/budget/subscription/receivable/expected-income workflows, and encrypted backup restore.
- Prior debug APK at the schema-8 milestone: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,049,960 bytes).
- Gradle verification used the cached Gradle 9.6.0 distribution copied into ignored `.ralledger-local` plus the local JDK 25. Repository access was needed to resolve Foojay and Android plugins. Project settings/wrapper versions were restored unchanged after diagnosis.

## Schema 9 — Bills, loans and repayment audit

- Added `debts` and `debt_payments` tables with migration 8→9; populated schema-1 and schema-2 fixtures now traverse the full chain through schema 9. The existing schema-8 backup format remains restorable with no debt records.
- Bills post a balanced expense/payable entry. Loan receipts post cash/liability only, so borrowed funds do not become income. Repayment entries split principal reduction from categorized interest expense.
- Added the Planning → Bills & loans screen with search, active/archived lists, bill and loan entry forms, due-date state, payment history, principal/interest payment entry, reversal, cancellation, metadata edit, and closed-record archive/restore. Posted amounts/categories stay immutable; permanent deletion of posted records is intentionally blocked to preserve journal history.
- Added validation to reject wrong category/account types, overpayment, negative/empty payment amounts, cancellation with unreversed payment history, and archiving an open debt. Account/category delete and merge operations now preserve linked debt history.
- Dashboard liabilities and payable totals include the new records; cash flow counts loan proceeds and debt cash payments without treating loan proceeds as income. Insights and budgets include bill recognition and interest while excluding principal from expense totals.
- Encrypted backup schema includes debts and payment events, validates journal/account/category references, reversal agreement and outstanding-principal reconciliation, and restores rows atomically.
- Passed full post-debt and account-reconciliation verification on `Medium_Phone(AVD) - 17`: debug build, 21 unit tests, lint (0 errors; 23 warnings), Android-test Kotlin compilation, and all 15 connected instrumentation tests. Coverage includes populated schema-1→9/schema-2→9 migrations, debt lifecycle and reversal, account reconciliation increases/decreases for assets and liabilities, category merge, and encrypted backup round-trip.
- Final interactive debt smoke passed on `Medium_Phone(AVD) - 17`: Bills & loans opens the correct screen; Add bill and Add loan show their expected fields; Subscriptions opens its own screen. Payment-form interaction and persistence after restart remain for hands-on QA.
- Account reconciliation UI smoke passed on `emulator-5554`: created synthetic `UI_Test_Wallet`, opened Reconcile balance, entered a verified balance and reason, and confirmed the posted balance update and success message. This synthetic account and adjustment remain on the development emulator; no real financial data was used.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,241,983 bytes). The unrelated `TestApplication/.idea/deploymentTargetSelector.xml` modification is user-local and remains outside the feature work.

## Schema 10 — Savings goals and reserved funds

- Added `savings_goals` and `savings_goal_events` with migration 9→10. Existing schema-1 and schema-2 synthetic migration fixtures now traverse the full chain through schema 10.
- Added Planning → Savings goals with target and optional target date, metadata editing, reserve/release actions, dated allocation history, archive/restore, and deletion restricted to goals with no allocation history.
- Reserves earmark existing dashboard-included liquid cash; they do not transfer money or create journal entries. Reserved funds reduce Available Money, but cash balances, total assets and net worth remain unchanged. Allocation is rejected if available cash is insufficient; releases cannot exceed the goal's reserved amount.
- Encrypted backups now include goal metadata and allocation events. Restore checks goal references, event types and dates, positive amounts, and event-derived reserved balances before atomic replacement.
- The full schema-10 verification on `Medium_Phone(AVD) - 17` passed before the final searchable-list UI edit: debug build, 21 unit tests, lint (0 errors; 23 warnings), Android-test Kotlin compilation and all 16 connected instrumentation tests. Coverage included schema-1→10/schema-2→10 migration chains and savings-goal lifecycle, over-allocation rejection, no-double-count accounting, and encrypted backup/restore.
- After the searchable-list edit, the source passed debug assembly, all 21 unit tests, lint (0 errors; 23 warnings), and Android-test Kotlin compilation at the prior checkpoint. A fresh rerun in this session could not reach project tasks: Gradle failed to resolve the configured `org.gradle.toolchains.foojay-resolver-convention:1.0.0` plugin, including in offline mode. The earlier connected rerun also could not start because ADB had no online emulator (`emulator-5562` remained offline and no AVDs were listed). The screen/editor compiled at the prior checkpoint; hands-on goal interaction and restart persistence remain for Android Studio validation.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,225,331 bytes). Automated tests used synthetic records; the previously documented `UI_Test_Wallet` remains on the development emulator if it reconnects.

## Schema 11 — Interest-bearing accounts and time deposits

- Added the schema 10→11 migration for interest-product profiles and posted-interest history. Profiles link to one compatible asset account; time deposits require a matching Time Deposit account and a positive original principal.
- Added an Actual/365 estimate using exact centavo arithmetic, configurable simple/daily/monthly/quarterly/annual compounding, basis-point annual/tax rates, and gross/tax/net projections. Estimates stay informational; only an explicitly recorded actual credit changes the journal.
- Interest credits post gross income and withholding-tax expense together, increasing the selected account only by net interest. Withholding tax uses its own expense category in Insights and monthly budget totals. Rate/tax snapshots and journal reversals preserve the audit trail. A 100% tax credit is supported without a zero-value account journal line.
- Added Planning → Interest & deposits with profile search/type/archive filters, terms editor, estimated interest, maturity status, actual-credit entry, history, reversal, archive/restore and safe delete-if-unused. Account archive also archives its linked interest profile and tells the user; restoring the profile remains a separate action.
- Encrypted backups now serialize and validate interest profiles, tax categories, credit/journal relationships, balances, reversal state, and older backups with no interest arrays. Archived profiles linked to archived accounts remain restorable.
- Pure domain tests were compiled with the locally cached Kotlin compiler and run with JUnit: 12 tests passed across interest estimates, monthly budget aggregation and transaction filters, including exact-centavo math, invalid terms, rounding, a daylight-saving calendar boundary, assigning interest tax to its separate expense-category budget, and finding/filtering the tax category. This isolates those modules only; it does not verify the Android module or Room integration.
- The first offline Gradle attempt stopped before project configuration because the Foojay plugin was not cached. After the user approved Gradle downloads, schema-11/12 Room code generation and Android compilation succeeded; the full connected suite could not execute because the only emulator was offline. Runtime migration, repository/backup instrumentation, and interest UI smoke therefore remain unverified.
- The current debug APK was rebuilt with schema-12 source after the approval (22,394,639 bytes); this is a debug verification artifact, not a release-signed APK.

## Schema 12 — Linked subscription payments

- Added a subscription payment-history table linked to its subscription, posted journal entry, cash account and expense category. The migration is additive; prior subscriptions remain without payment history.
- Recording a payment validates the active subscription, assigned liquid account, active expense category, positive amount and nonfuture date. It posts a balanced expense, updates the account ledger, stores amount/category/account/date/notes, and advances the recurring billing schedule past the payment date.
- Reversal posts the ordinary inverse journal entry, retains the event as reversed, and restores the previous payment date and billing due date when the current due date still matches the saved post-payment date. Later active payments must be reversed first.
- Added the payment dialog and expandable history; rows show date, category, account, amount, notes and reversal state. The newest unreversed payment can be reversed after confirmation. Payments feed budget, Insights, dashboard cash-flow and transaction expense/search filters.
- Encrypted backup serialization and integrity validation include payment references, balanced journals, category/account types, schedule snapshots and reversal-state consistency. Category merge reassigns payment category references; category/account foreign keys protect linked history.
- Added pure tests for schedule advancement and month-end cadence, transaction expense filters, budget aggregation and reversal-aware cash flow, plus repository instrumentation coverage for posting, schedule progression, reverse ordering, category merge, protected deletion, history and encrypted backup restore. A direct local Kotlin/JUnit run passed 32 pure domain/unit tests; the run uses a temporary stub only for the database system-account ID constants and does not compile Room, Compose or Android integration.
- Android/Room compilation, unit tests, lint and Android-test source compilation now pass after Gradle downloads were approved. Connected migration/payment/backup tests and device smoke remain unverified because the emulator is offline.

## Module readiness

| Module | Status | Limits |
|---|---|---|
| Money input and accounting engine | Implemented and unit-tested | Android screens still need hands-on validation |
| Accounts | Create, metadata edit, icon/color, dashboard inclusion, search, history, archive/restore, guarded unused delete, balance reconciliation and backup | Currency/type/opening balance intentionally immutable after creation; create and reconciliation dialog smoke-checked; remaining CRUD actions still need manual QA |
| Transactions | Income, expense, transfers/fees, loan proceeds and principal/interest payments, search, type/account/category/date filters, duplicate, reversal, and atomic correction by reversal plus replacement | No draft lifecycle, transaction type conversion, attachments/tags, or refunds; linked receivable/debt postings cannot use generic correction |
| Categories and income sources | Create, rename, recolor, re-icon, one-level subcategory, search, archive/restore, history, merge and unused delete; migration/backup tests pass | Income sources are represented by user-managed income categories; period source totals now appear in Insights. Full screen interactions still need manual QA |
| Receivables | Recognition, partial collection, editable client/description/due date, collection and write-off history, event reversals, cancellation, partial/full write-off, archive/restore, expense reporting and backup | Schema-8 connected lifecycle/migration/backup tests pass; emulator smoke covers representative create/edit/collect/over-collection/reversal/write-off/cancel/archive/restore/restart paths; additional edge cases remain; no receivable-level permanent delete because recognition is posted |
| Expected income | Forecast records with category/source, counterparty, amount and date; convert once to a receivable with recognition journal; backup link validation | Connected migration, lifecycle, category merge and backup tests pass; UI smoke covered create/cancel/delete and editor rendering; conversion uses the existing receivable recognition policy |
| Bills and personal loans | Bill recognition to expense/payable; loan proceeds to cash/liability without income classification; partial principal and interest payments; payment reversal, cancellation by journal reversal, metadata edit, closed-record archive/restore, due-date state, search and encrypted backup | Connected debt lifecycle, reversals, category merge, schema migration and backup restore pass. Emulator smoke covers create, split principal/interest payments, over-principal rejection, partial/full payment, reversal, cancellation, archive/restore and restart persistence; date/search/edit, insufficient-funds and small-screen cases remain; posted amount/category cannot be edited or deleted |
| Reconciliation | Atomic adjustments for asset/liability accounts implemented and unit-tested | Screen accessibility is limited; unmatched-transaction workflow is incomplete |
| Budgets | Monthly expense-category budgets; create/edit/archive/restore/confirmed delete; live spend, remaining amount and overspend indicators; safe category references and merge handling; encrypted backup support | Connected budget lifecycle and merge tests pass. No carry-forward, fund allocation, or multi-period budget rules |
| Subscriptions | Daily/weekly/monthly/quarterly/annual schedule create/edit, pause/resume/cancel/reactivate, archive/restore, guarded delete, normalized commitments, categorized ledger payment, history and ordered reversal | Schema-14 build, 51 unit tests and 22 connected tests pass, including migration, payment reversal and backup coverage; manual profile workflow and due reminders remain incomplete |
| Savings goals | Create/edit target, date and notes; searchable list; reserve/release existing liquid cash with dated history; archive/restore; delete only before use | Connected lifecycle and encrypted backup/restore pass. Emulator smoke covers create/edit, allocation and release, over-limit guards, delete-history protection, archive/restore with a live reserve, available-cash calculation and reserve-history persistence after restart; additional small-screen and backup UI checks remain |
| Interest-bearing savings and time deposits | Schema-11 profile terms, interest/tax estimate, compounding, maturity, actual gross/tax/net credits, history and journal reversal; archive/restore and delete-unused | Schema-14 migration, lifecycle, interest posting/reversal and encrypted-backup connected tests pass. Emulator smoke covers profile creation/edit, estimates, tax/net posting, reversal, deletion protection, profile/account archive and separate restore, and restart persistence; backup UI and smaller-screen checks remain |
| Backup and restore | Encrypted SAF flow, validation, atomic replacement, account descriptions, category hierarchy/archive state, budgets, expected income, savings goals/allocations, interest profiles/credits, bills/loans/payment audit trail, subscriptions/payment history, receivables and write-off history; schema 22 adds crash-recovery journal and commit marker | Backup data migrates and restores atomically in Room; cross-store settings now reconcile at startup. Connected recovery/migration execution and interrupted-process device checks still need an online emulator |
| Dashboard | Available Money is primary; Net Worth is separate; debt liabilities, payable totals, loan proceeds and reversal-aware month cash flow flow from the ledger | Available Money now uses calculated spendable funds after goal reserves; emulator values matched ₱1,000 account cash, ₱50/₱100 reserves and the corresponding spendable balance. User-specific account flags cannot be inspected without a device database |
| Appearance and insights UI | Saved theme mode/palette options; interactive daily, weekly, monthly, yearly, custom-range and all-time cash-flow/category charts | Must be checked on device for contrast, overflow, and small-screen layout; native home-screen widgets are still incomplete |
| Historical net worth/liquid-assets, savings growth, subscription/collection trend and aging reports, business reports, CSV/XLSX import/export, app lock, widgets | Incomplete | No end-to-end implementation in this checkpoint |

## Known audit findings and disposition

- The prior dashboard discrepancy is reproduced as a regression test under the stated account assumptions: liquid cash is ₱2,111; total assets and net worth are ₱17,111 when liabilities are zero and receivables are ₱15,000. The actual historical database flags were not available, so its specific cause is not claimed as proven.
- No existing user database was reset. Populated synthetic schema-1/schema-2 databases were migrated through schema 10 on the emulator. Schema-11/12 migration code compiles and Room schema generation passes, but runtime migration remains unverified because the emulator is offline; user-specific historical database contents and flags remain uninspected.
- The Android UI describes backup coverage accurately for previously runtime-verified records; schema-11 interest and schema-12 subscription-payment backup round-trips remain unverified. CSV, expanded reports, subscription notifications, app lock, saved views, and home-screen widgets remain outside this checkpoint.

## Android Studio validation checklist

- [ ] Open the project with JDK 25 and perform a clean debug build.
- [ ] Install and launch the debug APK on an emulator/device; confirm current user data remains intact after migration.
- [ ] Create an asset and liability account with opening balances; edit metadata and verify type/opening balances and journal history remain unchanged.
- [ ] Search accounts, inspect transaction history, archive/restore an account, delete an unused account, and confirm a used account cannot be deleted.
- [x] Emulator UI smoke: create a custom income source, rename it, archive it, show archived categories, restore it, and confirm unused deletion. The synthetic category was removed; no transaction was assigned to it.
- [ ] Finish manual category UI checks for subcategory reorganization, merge, and history display. Connected lifecycle instrumentation covers category merge and history protection; account/category screen behavior on smaller devices remains unverified.
- [ ] Record an income, expense, transfer with fee, and reversal; inspect ledger and month totals.
- [ ] Create a receivable, edit client/details/due date, collect partially, attempt overcollection, reverse a collection, partially/full write it off, reverse a write-off, cancel, archive/restore, and verify journal and payment/write-off history.
- [x] Run the connected suite on `Medium_Phone(AVD) - 17`: 22 tests passed with 0 failures/errors/skips. Populated schema-1/schema-2 migration chains reach schema 14; the suite covers account/category/budget/receivable/debt/expected-income/savings-goal/interest/subscription/labels/saved-filter lifecycle and encrypted backup/restore. This run includes the saved-filter and dashboard-widget changes.
- [x] Verify repository reconciliation corrections for asset and liability accounts with positive and negative differences while preserving original opening journal entries.
- [x] Smoke-check account-menu reconciliation on emulator; verified adjustment text, reason entry, saved balance and success feedback.
- [ ] Add subscriptions with daily, weekly, monthly, quarterly, and annual billing; edit a schedule, pause/resume, cancel/reactivate, archive/restore, and verify normalized dashboard total.
- [x] Subscription lifecycle, payment posting/reversal, archived state, backup, and migration coverage pass in the schema-13/14 connected suite; all five billing frequencies are represented in the current model/UI options.
- [x] Export an encrypted backup and restore it into an empty QA install; integrity validation passed and synthetic account, journal, and dashboard values matched. Broader record combinations, wrong-password/cancel recovery, and smaller-screen behavior remain open.
- [ ] Confirm user-created financial records survive app restart and inspect UI sizing, insets, dialogs, and navigation on multiple screen sizes. This emulator contains no user ledger records; smaller-screen review remains open.
- [x] Switch System/Ocean to Dark/Indigo, force-stop and relaunch, and confirm Dark/Indigo remain selected; restore the original System/Ocean settings afterward. Light and Sage combinations and chart-color contrast still need review.
- [ ] Validate day/week/month/year/custom/all-time Insights, custom date boundaries, reversals, category taps, source totals, and selected-bucket values against the journal.
- [x] Run `connectedDebugAndroidTest`; the latest full run passed 22 tests, including backup, migration, lifecycle, and concurrent collection coverage.
- [x] Smoke-check expected-income tab and editor; create, cancel, and delete a synthetic forecast and verify the list returns to empty.
- [x] Verify Bills & loans, Add bill/Add loan forms, and Subscriptions tab routing after the final mapping correction.
- [x] Smoke-check Bills & loans payment entry, principal/interest split, over-principal guard, reversal, cancellation, archive/restore and restart persistence on the emulator.
- [ ] Complete remaining debt date/search/edit, insufficient-funds and small-screen checks.
- [x] Smoke-check Savings goals create/edit, reserve/release, validation guards, archive/restore with an active reserve, available-cash calculation and reserve-history persistence after restart.
- [x] Resolve the Foojay plugin using the approved Gradle download; the latest run passed debug assembly, 53 unit tests, lint, Android-test source compilation, and 22 connected tests with 0 failures/errors/skips.
- [x] Run populated schema-11/12 migration, interest, and subscription-payment lifecycle/backup tests on `emulator-5554`; later migration fixtures pass through schema 14.
- [x] Smoke-check time-deposit profile creation/edit, estimates, actual credit and tax classification, reversal, deletion protection, profile/account archive and separate restore, and relaunch persistence.
- [ ] Smoke-check interest profile backup/restore UI and smaller-screen sizing.
- [ ] Finish hands-on account/category/receivable/subscription workflows, chart validation, and smaller-screen sizing. Theme, dashboard-widget, financial-default persistence, and a basic SAF backup/restore path have been smoke-checked; edge-case coverage remains.

## Next development steps

1. Finish manual category subcategory/merge/history flows, then complete receivable and transaction entry/reversal walkthroughs with synthetic data and cleanup.
2. Finish Bills & loans payment/reversal UI, restart persistence, and smaller-screen checks.
3. Manually verify the interest/time-deposit profile editor, estimates, actual credit/tax/reversal, archive behavior, and visible history; connected migration/repository/backup tests already pass.
4. Verify period and chart interactions against synthetic journal data, including custom boundaries, reversals, source totals, and small-screen chart labels.
5. Expand SAF restore checks across more record types and wrong-password/cancel recovery, then review data-export privacy, optional app lock, native home-screen widgets, and report gaps against the mission requirements.

## Insights period analytics — 2026-10-23

- Added a pure domain calculator for day, Monday-based week, month, year, custom inclusive date range, and all-time reporting. Charts bucket daily periods by day, yearly periods by month, and longer custom/all-time ranges by week, month or year to keep the display usable.
- Replaced the prior month-only screen calculations with a period selector, previous/next navigation, custom start/end date pickers, an interactive income-versus-expense bar chart, selected-bucket amounts, category spending donut, and income-source totals based on the user's income categories. Insights also shows subscription payments and receivable collections made during the selected period.
- Period totals read the persisted journal transaction projection. Original entries stay attributed to their posting dates; reversal entries reduce the appropriate income/expense/category/source in the reversal period. Receivable recognition is counted once as income, later collections are excluded from income, and transfer fees plus debt interest are expenses. Loan proceeds and principal repayments are not reported as income/expense.
- Corrected account distribution to include active asset accounts only and to show receivables in their separate section rather than mixing liabilities into assets.
- Added seven pure-domain unit tests for calendar period boundaries, leap-year/month ranges, custom inclusive dates/navigation, reversal date attribution, category reversal totals, income-source totals, transfer fees, subscription payments, receivable collections, buckets and empty all-time data.
- Focused verification: the calculator and seven tests compile with the already cached Kotlin 2.2.10 compiler/JUnit artifacts and pass in a local JVM run. This is not an Android/Compose build.
- After Gradle downloads were approved, Android compilation, unit tests, lint and Android-test source compilation passed for this Insights implementation. Device UI smoke and connected migration/backup tests remain unverified because the emulator was offline.
- Not completed in this analytics slice: historical net-worth/liquid-assets reconstruction, savings-growth charts, subscription cost trends, receivables aging trends, personal/business reports, CSV/XLSX flows, and device/UI smoke checks.

## Daily subscription billing — 2026-10-23

- Added a Daily billing frequency to subscription schedules and monthly commitment normalization (365 daily charges per year, rounded to the centavo).
- Daily schedule advancement skips every occurrence that falls on or before the actual payment time, consistent with overdue weekly/monthly schedule handling. The Compose form derives its options from the enum, so Daily appears in create/edit selectors without a separate screen change.
- The frequency is persisted by enum name in an existing string field; no Room schema migration is needed. Encrypted backup validation uses the enum parser and therefore recognizes the new value.
- Added a normalization unit assertion and a daily overdue-schedule test. The subscription model and schedule plus six focused existing/new tests compile and pass using cached Kotlin/JUnit artifacts.
- Android build, 40-test unit suite, lint and Android-test source compilation pass after the user approved Gradle downloads. Connected repository/backup verification remains unrun because the emulator was offline; no notifications or background billing automation were added.

## Previous Android verification — 2026-10-23

- The debug build initially exposed a compile error in backup validation: `accountsById` was declared below code that used it. Moved the lookup before its first use and removed the later duplicate declaration.
- After the user approved Gradle downloads, the combined `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:compileDebugAndroidTestKotlin` task completed successfully.
- Unit results: 40 tests, 0 failures, 0 errors, 0 skipped. This includes 7 Insights calculator tests and 6 subscription model/schedule tests.
- Lint completed with 0 errors and 23 warnings. Warnings include target API and deprecated Compose/icon API guidance.
- Android-test source compilation passed. The connected test task was attempted but skipped `emulator-5562` as OFFLINE and failed with “No online devices found.” ADB reconnect did not bring a device online.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,394,639 bytes; built 2026-10-23). It contains the current schema-12 source but is not release-signed. Schema-11/12 runtime migrations, repository/backup instrumentation, and current UI smoke are still pending an online emulator.
- No user database was reset or modified. The unrelated `TestApplication/.idea/deploymentTargetSelector.xml` local IDE change remains uncommitted.

## Transaction labels and connected verification — 2026-10-23

- Added persistent transaction labels with case-insensitive unique names, custom colors, multi-label assignment to journal entries, label search/filtering, edit, archive/restore, merge, and delete-if-unused. Archived labels stay attached to historical entries but cannot be newly assigned.
- Raised Room to schema 13 with `transaction_tags` and `journal_entry_tags`. Migration 12→13 creates both tables and indexes without rewriting existing financial rows. Populated schema-1 and schema-2 migration fixtures now traverse through schema 13.
- Encrypted backup/restore now includes label definitions and journal-entry assignments, validates names/colors/IDs/references, accepts earlier backups without label arrays, and restores in foreign-key-safe order.
- Added unit coverage for label search/filtering and connected coverage for label lifecycle, assignment, merge behavior, archive protections, history preservation, migration chains and encrypted backup round-trip.
- The connected suite exposed a subscription-payment API bug: its payment-record ID was returned where callers needed the journal-entry ID to reverse the posting. The repository now returns the journal-entry ID; the subscription payment history remains linked internally to the schedule.
- Verified on `Medium_Phone(AVD) - 17` / `emulator-5554`: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:compileDebugAndroidTestKotlin`, and `:app:connectedDebugAndroidTest` all succeeded. Results: 41 unit tests and 20 connected tests passed, with zero failures, errors, or skips. Lint reports 24 warnings and zero errors.
- Connected tests include schema-1/schema-2 migrations through schema 13, interest-product lifecycle/backup, subscription payment posting/reversal/backup, transaction correction, all previous lifecycle suites, and both transaction-label tests.
- Installed the debug APK over the existing development emulator app without clearing app data. The transaction journal, label manager, and new-label editor rendered; the dialogs fit the 1080×2400 emulator screen. No label or transaction was created during this visual check, and no user financial data was reset or changed.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,866,788 bytes; built 2026-10-23). This is a debug build, not a release-signed artifact. Manual label assignment/search/filter UI walkthrough and smaller-device sizing remain open.
- `TestApplication/.idea/deploymentTargetSelector.xml` remains the pre-existing local IDE change and is excluded from the implementation commit.

## Spreadsheet transaction export — 2026-10-23

- Added a Settings → Transaction CSV export using Android's document picker. It exports the posted journal with exact decimal amounts, fees, UTC and epoch timestamps, account/currency/category identifiers and names, counterparty, description, notes, reversal state and links, and label IDs/names.
- CSV text is quoted with embedded commas/quotes/newlines preserved; spreadsheet formula-like text is prefixed safely. The UTF-8 export includes a BOM for desktop spreadsheet compatibility. The UI warns that CSV is plaintext and should be saved privately; encrypted backup remains the full-data recovery format.
- Added unit tests for exact centavo formatting, deterministic ordering, reversal references, multiline/quoted text, label fields and formula-injection protection. No schema migration was needed.
- Verified the file-picker flow on `emulator-5554`; exported and inspected an empty journal CSV (header only), then removed the temporary test file from the emulator. No financial transaction was added.
- Passed the debug build, all 47 unit tests, lint (24 warnings, 0 errors), Android-test source compilation, and all 21 connected tests on `Medium_Phone(AVD) - 17` after the import changes.
- CSV planning records are not exported as separate tables; encrypted backup remains the complete recovery path.
- Gradle generated a local `TestApplication/.kotlin/` compiler-session directory; added it to `.gitignore` so it is not included in source commits.

## Mapped CSV transaction import — 2026-10-23

- Added a column-mapping import flow with header suggestions, editable field mappings, explicit month/day order, preview counts, row-level errors and a confirm step. Supports income, expenses and transfers into existing active accounts/categories; transfer fees and existing active transaction labels can be mapped too. Unknown labels are skipped with a warning.
- Added quoted CSV parsing with embedded commas/newlines and UTF-8 BOM support, bounded to 20 MB and 50,000 rows. Account/category names must resolve uniquely (IDs also work); currencies and transfer account compatibility are checked. Ambiguous slash dates default to month-first but can be switched to day-first.
- Import preview detects existing and within-file duplicates. Each accepted row is posted via the accounting engine inside its own database transaction, preserving balanced journal entries. A second database duplicate check protects against re-imports and concurrent imports; invalid rows fail independently and are reported.
- Automated coverage includes parser quoting/BOM, mapping, ambiguous date order, unknown references, duplicate detection, export format, and connected accounting integrity. The connected import test verifies balanced journal posting, the expected net account balance and repeated-import duplicate skipping.
- Emulator UI smoke opened a synthetic CSV with a deliberately unmatched account. Header suggestions populated, preview showed `Ready 0 · Duplicates 0 · Needs attention 1` and the precise account-resolution error, and the import action remained disabled. The dialog was cancelled; the synthetic file was removed. No financial records were created.
- Import is intentionally limited to income, expense and transfer rows and requires matching active RalLedger accounts/categories. Receivables, debts, subscriptions, budget/planning records and custom record types are not imported through CSV. The current mapper is generic rather than a dedicated MyMoney preset; users can map MyMoney-like CSV columns manually. CSV import/export does not replace encrypted backup.
- Latest debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,866,847 bytes; debug-signed, not a release APK). Build and automated checks pass; wider manual CRUD workflows, smaller-device sizing, release signing, and remaining requirements in the matrix remain open.

## Saved transaction filters — 2026-10-23

- Added Room schema 14 storage and the 13→14 migration for named transaction filters. Names are trimmed, case-insensitively unique, and limited to 40 characters; saved search text is limited to 200 characters and invalid date ranges are rejected. Filter references are kept as preferences without foreign keys, so removing a category, account, or label cannot block ledger changes.
- Added journal controls to save a configured combination of search, transaction type, account, category, label, and date range; apply it; update its criteria from the current controls; rename it; or confirm deletion. Clearing filters now also clears the search text.
- Encrypted backup and restore now include saved filters and validate names, criteria, enum values, timestamps, and duplicate IDs/names. Older backups without a saved-filter array still restore with no saved filters. The restore preview and Settings description report this data.
- Added connected coverage for create/list, case-insensitive duplicate rejection, rename, criteria update, delete, encrypted backup/restore, and proving journal entries/lines remain unchanged. Populated schema-1 and schema-2 migration fixtures traverse through schema 14 and preserve their existing records.
- Passed on `Medium_Phone(AVD) - 17`: debug assembly, all 47 unit tests, lint (24 warnings, 0 errors), Android-test Kotlin compilation, and all 22 connected tests (0 failures, errors, or skips). The connected suite includes the new saved-filter lifecycle/backup test and schema-14 migration checks.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (22,933,919 bytes; SHA-256 `86F5703E5921DB72B442CE39F574EDFDB9EA7EB75F31960136606E1C294CA609`; built 2026-10-23; debug-signed, not a release APK). Smaller-device sizing remains pending. No financial data was entered or changed by the saved-filter connected test; it used in-memory databases.
- The pre-existing local `TestApplication/.idea/deploymentTargetSelector.xml` change remains untouched and excluded from implementation commits.

## Narrow-screen layout and current build — 2026-10-23

- Simplified the Settings heading and removed its redundant helper copy. Theme-mode and palette chips now flow onto a second row instead of compressing and breaking words.
- On screens narrower than 320 dp, bottom navigation keeps its icons and accessible descriptions while hiding labels that would otherwise split mid-word.
- Shortened the Budgets empty state to `No budgets for this month.`; on the narrow viewport it no longer runs beneath the floating Add budget button.
- Installed the rebuilt APK and manually checked Overview, Accounts, Transaction Journal, Budgets, Insights and Settings at 720×1600 physical pixels (274 dp logical width). The toolbar actions stayed separated, Settings chips remained readable, and the Budget empty state remained clear of the add button. Restored the emulator to 1080×2400 afterward.
- Re-ran `:app:assembleDebug`, `:app:testDebugUnitTest` (51 tests; 0 failures), `:app:lintDebug` (24 warnings; 0 errors), Android-test Kotlin compilation, and `:app:connectedDebugAndroidTest` (22 tests; 0 failures, errors or skips). The full build succeeded.
- Current APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (23,502,789 bytes; SHA-256 `F276AEB7D12B41A6B81323A3DA21F697CCA471EBBB7D41A77585555A8C3843F2`; debug-signed, not release-signed).
- Publishing remains blocked by the missing destination: this checkout has no Git remote, and GitHub search/listing found no RalLedger repository under the connected account. The `Programming_Projects` repository has no RalLedger path and is not being used. The exact repository URL/name is needed before pushing to `main` or uploading the APK.
- Other live-use release gaps in the CRUD matrix remain open. The pre-existing local `TestApplication/.idea/deploymentTargetSelector.xml` change is excluded from this implementation.

## Category lifecycle and appearance follow-up — 2026-10-23

- On `emulator-5554`, created the synthetic income source `CodexQAIncomeStream`, renamed it to `CodexQAIncomeStreamEdited`, archived it, enabled the archived-category view, restored it, and confirmed deletion through the unused-category confirmation. Each operation returned its success message and the filtered list reflected the updates.
- No transaction was assigned to the test category, and no financial records were changed. The category was permanently removed after the smoke test.
- Changed appearance from System/Ocean to Dark/Indigo, force-stopped and relaunched the app, and visually confirmed the selected theme and palette persisted. Restored System/Ocean afterward.
- Connected instrumentation remains the evidence for category merge/history protections; manual category history, subcategory reorganization and merge flows remain open. Appearance checks for Light/Sage and chart contrast also remain open.
- Manual flow verified on `emulator-5554` after the saved-filter implementation: saved and applied a temporary expense filter, changed current criteria and updated the saved filter, renamed it, then confirmed deletion. The manager initially clipped the final action; its controls now use two rows and all four actions fit. The temporary filter was removed, the development emulator had no transaction records, and no journal data was changed.

## Dashboard widget customization — 2026-10-23

- Added six independently configurable Home summaries: Available Money, Net Worth, Reserved savings, Money owed, This month, and Recurring subscriptions. Settings lets users show or hide each summary and move it up or down; all summaries still use the existing persisted financial calculations.
- Dashboard layout preferences persist locally with DataStore. Unknown or duplicate saved order values are discarded, missing widget values are restored to the order, and missing visibility defaults to all visible.
- Added unit coverage for preference defaults/decoding, unknown and duplicate entries, reordering boundaries, and hidden-widget rendering order.
- Passed `:app:assembleDebug`, `:app:testDebugUnitTest` (51 tests, 0 failures), `:app:lintDebug` (24 warnings, 0 errors), Android-test Kotlin compilation, and `:app:connectedDebugAndroidTest` (22 tests, 0 failures).
- Manually checked on `emulator-5554`: moved Net Worth above Available Money, hid Reserved savings, confirmed the Home layout, force-stopped and reopened the app, and confirmed the custom order and visibility persisted. Restored the default order and all-visible settings afterward. No financial records were created or changed.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (23,502,800 bytes; SHA-256 `48CE64F2E879A5DBD4337FF4E21E92BB5D5EE6022701DFC224B6ED15BE57909C`; built 2026-10-23; debug-signed, not release-signed).
- The broader CRUD matrix and live-use release requirements remain incomplete. The local checkout has no Git remote, and the connected GitHub account did not expose a RalLedger repository; publishing source to `main` or uploading the APK requires the exact destination repository.

## Account lifecycle emulator follow-up — 2026-10-23

- Fixed the archived-account card layout: the account name is constrained to one line with ellipsis, and the Archived status is shown on a separate line so it cannot wrap vertically beside a long name.
- Installed the rebuilt debug APK on `emulator-5554` and visually confirmed the archived badge is horizontal and the card height remains normal. The first layout attempt did not solve the wrap and was replaced before this verification.
- Created a synthetic zero-balance account, archived it, restored it, and deleted it through the UI. The final screen confirmed permanent deletion; no user financial data was entered or changed.
- Re-ran the full Android verification after the final layout patch: `:app:assembleDebug`, `:app:testDebugUnitTest` (51 tests, 0 failures), `:app:lintDebug` (24 warnings, 0 errors), Android-test Kotlin compilation, and `:app:connectedDebugAndroidTest` (22 tests, 0 failures, errors, or skips) all passed.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk` (23,502,800 bytes; SHA-256 `30D6DCED6D96D2526352B5A2C513963CFC585C58EBCA95675C939180A3FD2971`; debug-signed, not release-signed).
- The user requested publishing to `main` and uploading the APK. This checkout has no configured Git remote; the connected GitHub account is `Ralimations`, where repository search found no RalLedger repository. The unrelated `Programming_Projects` repository is not a safe destination. Publishing is pending the exact repository URL/name and its intended APK delivery method.
- The pre-existing local `TestApplication/.idea/deploymentTargetSelector.xml` change remains untouched and excluded from implementation commits.

## Receivables emulator lifecycle follow-up — 2026-10-23

- Used an isolated `com.ralledger.qa` debug package on `emulator-5554`; the existing `com.ralledger` app and its data were not changed during receivables QA.
- Created a synthetic ₱100 receivable, edited its client details, collected ₱25, and confirmed the card showed ₱75 outstanding. An attempted ₱100 collection was rejected with “Collection exceeds the remaining balance” and left the balance unchanged.
- Reversed the collection and confirmed the balance returned to ₱100 while the original event remained in history as Reversed. Recorded a ₱10 partial write-off with a test note, confirmed the balance fell to ₱90, reversed it, and confirmed the balance returned to ₱100 with the write-off preserved as Reversed.
- Cancelled the receivable after reversing both events. The app confirmed that a reversing journal entry was posted; the card showed Cancelled and ₱0 outstanding. Archived and restored the record, then force-stopped and reopened the QA app and confirmed the cancelled record persisted.
- Uninstalled the QA package and removed its temporary application-ID suffix from the debug build configuration. The normal package and existing development data were retained. The emulator resolution remained 1080×2400.
- Rebuilt and installed the normal `com.ralledger` debug APK over the existing app without clearing its data. `:app:assembleDebug` succeeded; the APK was 22,591,575 bytes with SHA-256 `113AB5CF153E3BA67B6F4674B328F8D6E7349FF7089DD6D54883E6DE5F19DC15`.
- These checks cover representative receivables UI paths; full QA for other records, edge cases, and release workflows remains open.

## Bills and loans emulator lifecycle follow-up — 2026-10-23

- Used the isolated `com.ralledger.qa` package and a synthetic `QA wallet`; the installed `com.ralledger` app and its data were not used for these records.
- Created a ₱500 loan into the QA wallet. Posted a ₱100 principal/₱10 interest payment and confirmed outstanding fell to ₱400. A ₱500 principal attempt was rejected with “Principal cannot exceed the outstanding balance.” Reversed the partial payment and confirmed the loan returned to ₱500 with the original event retained as Reversed.
- Paid the remaining ₱500 principal; the loan showed Paid and ₱0 outstanding. Archived and restored it, confirming the payment history remained visible. Reversed the payoff, confirmed the loan returned to ₱500, then cancelled it after all payments were reversed; the card showed Cancelled and ₱0 outstanding.
- Created a ₱200 bill in Food & Dining. A ₱50 principal/₱5 interest payment reduced it to ₱150. Reversing that payment restored ₱200 and preserved the history as Reversed. Cancelling the bill after reversal showed Cancelled and ₱0 outstanding; archive/restore kept its history visible.
- Force-stopped and reopened the QA app. Both cancelled records and their reversed payment histories persisted. The QA wallet returned to ₱0 after the loan cancellation, and the Bills due and Loans outstanding summaries both showed ₱0.
- Uninstalled `com.ralledger.qa`, restored the standard debug application ID, rebuilt `:app:assembleDebug`, and installed the normal `com.ralledger` APK over the existing package without clearing data. The emulator remained at 1080×2400. At that checkpoint, the APK was 22,591,575 bytes with SHA-256 `113AB5CF153E3BA67B6F4674B328F8D6E7349FF7089DD6D54883E6DE5F19DC15`.
- Remaining debt QA includes more date/search/edit and insufficient-funds cases; the release checklist remains open.

## Savings-goal UI and available-cash follow-up — 2026-10-23

- Used a clean, isolated `com.ralledger.qa` install with a synthetic bank account holding ₱1,000 and a ₱500 savings goal. The existing `com.ralledger` app and its records were preserved.
- Reserved ₱100 and confirmed the goal history recorded the event while account cash, assets, and net worth stayed at ₱1,000. The Home Available Money tile incorrectly remained at ₱1,000 even though Reserved savings showed ₱100.
- Traced the mismatch to the Home widget reading raw liquid cash instead of the already-calculated `availableToSpendFunds`. Changed `DashboardScreen.kt` to display the calculated available amount. A fresh emulator view then showed Available Money ₱900, Reserved savings ₱100, and assets/net worth ₱1,000; after releasing ₱50, it showed ₱950 available and ₱50 reserved.
- Attempting to reserve ₱950 with only ₱900 available was rejected without changing the allocation. Releasing ₱150 against a ₱100 reserve was also rejected. A valid ₱50 release was recorded with its note and the event history remained visible after force-stop/restart.
- Edited the goal name to `Emergency2027`; attempted permanent deletion with allocation history and confirmed the guard refused with an archive-preservation message. Archived and restored the goal while ₱50 remained reserved; Home continued to show ₱950 available and ₱50 reserved while archived. Releasing the remaining ₱50 restored Available Money to ₱1,000 and Reserved savings to ₱0, with account assets and net worth unchanged.
- Removed the isolated QA package and temporary `.qa` application-ID suffix, then rebuilt and installed the standard `com.ralledger` debug APK over the existing app without clearing its data. `:app:assembleDebug` succeeded. APK size is 22,591,575 bytes; SHA-256 is `0716EDFA2E8E10D43FF06CB8D59FD14C628AB83B18375E1952DAF4B1C0EAD365`. `aapt` reports application ID `com.ralledger`; the emulator package query shows no QA variant.
- The full 51-unit/22-connected-test suite was not rerun for this dashboard binding correction; it had passed earlier on the same source state before this correction. This follow-up build and manual emulator checks passed. The broader release checklist remains open.

## Interest and time-deposit emulator follow-up — 2026-10-23

- Used isolated `com.ralledger.qa` data with a synthetic `QA_Deposit` Time Deposit account and ₱10,000 opening principal. The standard `com.ralledger` package and its records remained intact.
- Created a monthly-compounding time-deposit profile at 5% annual interest and 20% withholding tax, opened 2026-10-23 with maturity 2027-10-23. The screen showed a ₱511.62 gross and ₱409.30 net full-term estimate. Editing the rate to 6% changed the estimate to ₱616.78 gross/₱493.42 net while preserving the ₱10,000 account balance and the historical credit snapshot.
- Posted actual gross interest of ₱100 with ₱20 withholding. The account increased by ₱80 to ₱10,080, and the profile history showed gross, tax, and net separately. Reversing the credit restored the account to ₱10,000 and retained the original history marked Reversed.
- Confirmed the delete-unused guard rejects a profile with posted credit history and directs the user to archive. Archived/restored the profile and confirmed its terms and reversed credit remained. Archiving the account also archived the profile; restoring the account left the profile archived with an explicit separate-restore message. Restored the profile independently.
- Force-stopped and relaunched the QA app. The ₱10,000 Time Deposit asset, 6% profile, and reversed ₱100/₱20 credit history persisted. Home correctly counted ₱10,000 in assets/net worth and ₱0 as Available Money because a Time Deposit is non-liquid.
- Removed `com.ralledger.qa` and the temporary debug suffix; rebuilt and installed the standard package without clearing its data. `:app:assembleDebug` succeeded. The current APK is 22,591,575 bytes, SHA-256 `0716EDFA2E8E10D43FF06CB8D59FD14C628AB83B18375E1952DAF4B1C0EAD365`; both manifest and device package query identify only `com.ralledger`.
- This profile follow-up was manual UI verification; the full automated suite was not rerun. Backup/restore UI and smaller-screen profile layout remain open.

## SAF backup and navigation follow-up — 2026-10-23

- On an isolated `com.ralledger.qa` install, created an encrypted backup through Android's Save As picker. The preview reported 3 accounts, 14 categories, 2 journal entries and 4 journal lines. After clearing only the QA app's data, restored the backup through Android's Open picker; integrity validation passed and the restore completed.
- After restore, confirmed `QA_Backup_Wallet` showed ₱2,425 and Activity showed the ₱75 `QA_Backup_Expense` plus the ₱2,500 opening entry. This was synthetic QA data. The temporary backup file was removed from emulator Downloads after verification.
- Fixed bottom-tab navigation when Settings or Categories is on top of a tab route. Emulator checks confirmed Home opens from Settings and from Categories opened through Settings. Available Money and Net Worth continued to show ₱2,425 after restore.
- Removed the QA application-ID suffix and uninstalled `com.ralledger.qa`. Rebuilt and installed the standard debug APK over `com.ralledger` without clearing its data; the emulator package list now contains only `com.ralledger`.
- Latest standard debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 22,591,575 bytes; SHA-256 `8E7CB0C1B80D51F610717EE1681C66FE03B2B26BC15A8B0CDF316DA930A2D3AA`. This is debug-signed, not release-signed.
- This follow-up ran `:app:assembleDebug` and manual emulator checks only; the 51-unit/22-connected-test suite was not rerun. Wrong-password/cancel/failure recovery, broader backup data combinations and small-screen checks remain open.
- Publishing is pending because this checkout has no Git remote, and the connected GitHub account has no RalLedger/Ral-ledger repository. No repository was selected or created, so nothing was pushed and the APK was not uploaded.

## Financial default preferences — 2026-10-23

- Added local DataStore settings for the default quick-transaction account and the default Insights period. The account menu offers Automatic (first active asset account) or an active asset account; if a saved account is archived or unavailable, quick entry falls back to the first eligible active account.
- Insights can open with Day, Week, Month, Year, or All time selected. Custom ranges remain an in-session selection and cannot be saved as a default; invalid persisted values safely fall back to Month.
- Added two unit tests for supported reporting-period persistence decoding and fallback behavior. At this checkpoint, Gradle passed `assembleDebug`, all 53 then-current unit tests, lint, Android-test source compilation, and all 22 connected tests on `emulator-5554` with 0 failures, errors, or skips.
- Manual emulator QA selected Week and confirmed Insights opened on 19–25 Oct 2026; selected a synthetic wallet and confirmed it appeared in Quick Add after relaunch; then restored Automatic/Month and permanently deleted the unused zero-balance QA wallet. No transaction was posted.
- The debug APK at `TestApplication/app/build/outputs/apk/debug/app-debug.apk` is 22,842,244 bytes with SHA-256 `F470A8004255125C14EA91C9630419674EF846448D26F7CCC106FDFE9F3D81B0`. It remains debug-signed. Date format, currency display, and transaction-list display preferences remain unimplemented; release readiness is still open.

## Receivables aging analytics — 2026-10-23

- Replaced status-only receivables totals in Insights with current outstanding balances grouped by due-date age: not yet due, due today, 1–30, 31–60, 61–90, and 91+ days overdue. Each band shows its balance and record count; paid, draft, cancelled, written-off, archived, and zero-balance receivables are excluded.
- Insights also labels collections for the selected period as net of reversals. The collection total continues to come from posted journal-backed transaction projections; the aging snapshot uses current persisted receivable balances and the current local date.
- Added unit tests for every aging boundary, counts, aggregation, and exclusion rules. Full verification passed: debug assembly, 55 unit tests, lint, Android-test source compilation, and 22 connected tests on `emulator-5554` with 0 failures/errors/skips. The new aging presentation was build-verified but has not yet had a separate manual screen walkthrough.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 22,842,212 bytes; SHA-256 `0EDB3979FA8585DD6870998E8305450A5556A80C7D4319B52211551C4C0ACF92`. It is debug-signed and not suitable as a production-signed release.
- Savings-growth analytics and personal/business reports remain open. Journal-derived historical net-worth/liquid-asset tracking and gross/tax/net interest reporting are implemented and automated-test verified but still need manual UI inspection with populated synthetic records. App lock, further preference customization, and smaller-screen verification also remain release work.
- Clarified the CSV export notice: exports contain transaction details, are unencrypted, should be stored privately, and the password-protected backup is available for secure storage. This is a UI copy change; no credentials or export data behavior changed. Optional app lock and secure session handling remain unimplemented.

## Net-worth and liquid-asset history — 2026-10-23

- Added a read-only journal-history flow and a period-end balance calculator for the selected Insights range. It replays posted debit/credit lines for included asset and liability accounts plus receivable/debt ledger IDs, so openings, collections, repayments, transfers and reversal entries affect the historical balance on their posting dates.
- Insights now plots Net Worth and Liquid Assets over the selected day/week/month/year/custom/all-time buckets, with the period-end values shown below. It follows the same current account archive and dashboard-inclusion settings as Home; it does not rewrite or synthesize ledger records.
- Added unit coverage for period cutoffs, receivable assets, debt liabilities, liquid balances, included/excluded accounts, unknown ledger IDs, and reversal postings. The full run passed debug assembly, 57 unit tests, lint, Android-test source compilation, and 22 connected tests on `emulator-5554` with 0 failures/errors/skips. The new graph has not yet had a manual emulator walkthrough with populated synthetic records.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 22,856,387 bytes; SHA-256 `2D14C918906A8E29D07B9D6028A3E36492E7E6878742127D50951DDBBF8E3A35`. It is debug-signed, not a production release APK.

## Interest earnings breakdown — 2026-10-23

- Insights now separates gross interest credited, withholding tax, and net interest for the selected period. Reversal entries reduce each amount on the reversal posting date, preserving the ledger's date attribution.
- Added unit coverage for gross/tax/net totals and their reversal-period effect. Full verification passed debug assembly, 58 unit tests, lint, Android-test source compilation, and 22 connected tests on `emulator-5554` with 0 failures/errors/skips.
- Latest debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 22,857,160 bytes; SHA-256 `85E0230C62A7BF623CDFA3372734C86FE5340F67690F912C9E6890E5F89214BC`. It is debug-signed; historical and interest charts still need manual populated-data UI walkthroughs.

## Optional app lock and publishing preparation — 2026-10-23

- Added an opt-in app lock stored in DataStore. Enabling requires a successful Android system-authentication prompt; supported unlock methods are strong biometrics or the device credential on Android 11+, and device credential on Android 8–10. RalLedger does not store a separate PIN or biometric data.
- The activity waits for the saved lock setting before showing financial screens, requests authentication on launch and after the app leaves the foreground, and enables Android's secure-window flag while the lock is on. Settings exposes the toggle and sends users to Android Security Settings if a secure screen lock is unavailable.
- Added unit tests for the disabled-by-default preference and stored values. Final-source verification passed: `assembleDebug`, 60 unit tests, Android-test compilation, lint (0 errors; 24 warnings), and 22 connected tests on `emulator-5554` (0 failures, errors or skips).
- Installed the debug APK on the 1080×2400 emulator and confirmed Settings shows the App lock card and the expected secure-screen-lock setup message. The emulator has no secure screen credential, so the actual biometric/device-credential prompt, foreground return and re-lock flow were not exercised. This check entered no financial records.
- Packaged the debug APK at `releases/RalLedger-debug.apk` (22,793,229 bytes; SHA-256 `8A9579B1F8CD4F321DF6190A2B0A857E5F47E9031F2751B4D0C891CF60070B99`). It is debug-signed, and the manifest still reports version `1.0`; no production signing configuration is present.
- The user requested pushing to `main` and placing the APK in GitHub. This checkout has no Git remote, and the connected GitHub account's 36 accessible repositories contain no RalLedger match. `Ralimations/Programming_Projects` does not contain this project. The APK is prepared locally; publishing requires the exact destination repository URL/name.

## Receivable category integrity and publishing follow-up — 2026-10-23

- Added a restrictive foreign key from `receivables.linkedCategoryId` to `categories.id`, with an index. Migration 14→15 rebuilds the table and copies each existing receivable field, including archive state. An orphan reference causes the migration to fail transactionally instead of silently removing or rewriting financial history.
- Category deletion now checks receivable references and explains that referenced categories should be archived. The database constraint also blocks direct deletion, while category merge reassigns receivables before removing the source category.
- Instrumentation covers the schema-1 migration retaining a linked category, repository/direct-database deletion guards, and merge reassignment. Verification passed `assembleDebug`, 61 unit tests, lint (24 warnings, 0 errors), Android-test Kotlin compilation, and 23 connected tests (0 failures, errors, or skips). A focused rerun of the deletion/merge test also passed.
- Packaged the checked debug APK at `releases/RalLedger-debug.apk` (23,362,724 bytes; SHA-256 `C3E3CBE6BDB53E039514E1F1DFC2E261CA8D627A275DA6494624F245F7E09F12`). It is debug-signed; the manifest still reports version `1.0`, and no production signing configuration is present.
- The user named `RalLedger-finance-management` as the GitHub destination. Search and a direct lookup under the connected `Ralimations` account returned no repository (404); the local checkout has no Git remote, and the connected GitHub tools do not expose repository creation. No commit was pushed and no APK was uploaded. Publishing to `main` is pending an existing repository being created or made accessible, with its owner/name and write access.
- The pre-existing local `TestApplication/.idea/deploymentTargetSelector.xml` change remains untouched and excluded.

## User-controlled display preferences — 2026-10-23

- Settings now persist three display choices in DataStore: date order (`MMM d, yyyy`, `d MMM yyyy`, or `yyyy-MM-dd`), currency symbol versus ISO currency code, and comfortable versus compact transaction-list spacing. Older installs and invalid saved values fall back to the existing date/currency presentation and comfortable spacing.
- Date choices apply to visible transaction, account/category history, planning, receivable, subscription, savings, interest-credit and Insights dates. Editable date-entry fields and stored timestamps remain unchanged. Currency style applies to on-screen money summaries; amount-entry fields retain their numeric input format. These settings change presentation only and do not modify ledger records.
- Added unit coverage for display-preference fallback, date formatting and symbol/code formatting. Final verification passed debug APK assembly, 63 unit tests, lint (24 warnings, 0 errors), Android-test compilation and 23 connected tests on `emulator-5554` (0 failures, errors or skips). `emulator-5562` was offline and skipped. Emulator smoke on the 1080×2400 device confirmed date order, PHP code display and compact spacing persist after a force-stop/relaunch; the currency setting changed Home amounts to `PHP 0.00`. Test preferences were reset to the defaults afterward.
- Refreshed `releases/RalLedger-debug.apk`: 22,892,775 bytes; SHA-256 `C4D273F2F93E73955806C45158FB2729C99E1EF0645E179902CD6EBF9FCA6F8B`. It remains debug-signed; the manifest version is still `1.0`, with no production signing configuration.
- The user created `Ralimations/RalLedger-finance-management` and the checkout now uses it as `origin`. Commit `789ac99` was pushed to the new `main` branch; it includes `releases/RalLedger-debug.apk` and the refreshed checksum above.

## Subscription due reminders — 2026-10-23

- Added an opt-in subscription reminder setting with a notification-permission request and day-of, one-day-before, or three-days-before choices. Active, non-archived subscriptions receive one inexact Android alarm at 9:00 AM local time for the selected reminder date; past reminder times are skipped. Paused/cancelled/archived/deleted subscriptions and disabled reminders are not scheduled. Delivery rechecks the saved preference and current subscription before posting a private notification with a generic lock-screen preview. Alarms are restored after boot and recalculated after date, time, or timezone changes; no exact-alarm permission is requested.
- Final-source verification passed `assembleDebug`, 66 unit tests, lint (28 warnings, 0 errors), Android-test Kotlin compilation, and 23 connected tests on `emulator-5554` (0 failures, errors, or skips). `emulator-5562` was offline. The settings screen was visually smoke-checked in a temporary isolated `.qa` package: the notification permission prompt appeared and enabling reminders displayed all three timing choices. No financial records were entered.
- During follow-up restart/tap checks, the emulator repeatedly reported an input-dispatch ANR for the app; its cause was not isolated. Reminder setting persistence across restart and actual notification delivery therefore remain unverified manually. Scheduling date calculation and preference decoding are unit-tested. The temporary `.qa` identifier and isolated package were removed before the release build.
- Packaged `releases/RalLedger-debug.apk`: 22,843,360 bytes; SHA-256 `70FAE71EA68D66DD68DF65025620FB87FB298FAB68492303AA64123E2D70F414`. This is a debug-signed APK with version `1.0`; production signing is not configured.
- Published commit `cad9635c13ba75a697bf7fe07701c370ed577a62` and `releases/RalLedger-debug.apk` to `Ralimations/RalLedger-finance-management` on `main`.
- Remaining release work includes diagnosing the emulator ANR, manually verifying a scheduled notification with a sample subscription, and the broader CRUD, security, and small-screen checks listed above.

## Encrypted backup of user customizations — 2026-10-23

- Encrypted backups now carry the user's theme and palette, dashboard widget order and visibility, default transaction account, default Insights period, date and currency display, transaction-list density, and subscription reminder preferences. The optional `userPreferences` object is additive to the existing backup format: older backups remain valid and keep the destination's current preferences.
- Restore validates preference values and the default account against the backup's active asset accounts. A stale or archived account selection is exported as Automatic. Device app-lock state is intentionally not transferred.
- Financial records still restore in one Room transaction. User settings reside in separate DataStore files; restore rolls them back if a handled write or record-verification error occurs. A process termination in the narrow interval between preference writes and Room commit can leave settings and financial records from different restore states; fully atomic cross-store restore remains open.
- A previous `:app:connectedDebugAndroidTest` run on `emulator-5554` passed 25 tests with 0 failures or skips, including preference round-trip and legacy-backup compatibility. On the final source, `:app:assembleDebug`, `:app:testDebugUnitTest` (66 tests, 0 failures/errors/skips), `:app:lintDebug` (28 warnings, 0 errors), and `:app:compileDebugAndroidTestKotlin` passed.
- Refreshed `releases/RalLedger-debug.apk`: 23,430,221 bytes; SHA-256 `1839A368E93E66685EC7F1ED24346635A0C918975176DC9236A77B558A486128`. It is debug-signed, manifest version `1.0`; production signing remains unconfigured.
- The latest connected-task attempt reported 0 tests. A direct backup-class run was aborted with `INSTRUMENTATION_ABORTED: System has crashed`; Android System UI and Phone processes also reported startup ANRs, and Package Manager was unavailable during reboot. The new preference-write failure/rollback/retry case is compiled but not emulator-verified.
- At the preceding checkpoint, this continuation remained on `feature/ralledger-v2-live-ready`; the current counterparty update and refreshed APK are recorded below. Manual SAF settings round-trip, cross-store crash consistency, wrong-password/cancel recovery, and small-screen checks remain open.

## GitHub publication — 2026-10-23

- Merged `feature/ralledger-v2-live-ready` with the latest `main` history and published merge commit `5409dd2a5ad37ce548f7eedf556cc372d2bf0a68` to `Ralimations/RalLedger-finance-management` on `main`.
- Published `releases/RalLedger-debug.apk` (23,430,221 bytes; SHA-256 `1839A368E93E66685EC7F1ED24346635A0C918975176DC9236A77B558A486128`). GitHub reports the expected APK blob and size on `main`.
- The published source matches the verified feature snapshot: `assembleDebug`, 66 unit tests, Android-test Kotlin compilation, and lint (28 warnings, 0 errors) passed. The latest connected instrumentation attempt did not produce a test result because the emulator's Android system server crashed; the final rollback/retry instrumentation case remains unverified.
- The APK is debug-signed, manifest version `1.0`; production signing is not configured. Remaining release-readiness gaps include cross-store crash consistency, wrong-password/cancel recovery, reminder delivery, app-lock interaction on a secured device, small-screen checks, and incomplete CRUD modules listed in `FEATURE_CRUD_MATRIX.md`.

## Saved clients and counterparty links — 2026-10-24

- Added a persisted counterparty directory with case- and whitespace-normalized duplicate prevention, client/supplier/both/other types, notes, search, edit, archive/restore, merge and guarded deletion.
- Receivables and expected-income forecasts can link to a saved counterparty or keep using free text. Linked names follow a rename; converting a forecast carries the link to its receivable. Existing journal descriptions remain unchanged.
- Added Room schema 16 and nullable restrictive foreign keys from receivables and expected income. Encrypted backups carry counterparties and their record links while remaining compatible with backups created before this schema.
- Extended saved-counterparty links to bills, loans and subscriptions in Room schema 17. Renames and merges update current display metadata while leaving posted journal descriptions intact; reference counts, delete guards, backup/restore and the debt/subscription forms include these links. The schema-1 and schema-2 migration fixtures now target the current schema and assert that legacy subscriptions remain unlinked.
- The focused lifecycle and backup instrumentation now covers debt and subscription references, rename/merge behavior, unchanged journal history and restoring every link. The updated app and instrumentation sources compile, all 67 unit tests pass, lint passes and the debug APK assembles. One emulator run reached the test and failed on an assertion that expected the renamed contact before the rename step; that assertion was corrected. A rerun could not start because the emulator went offline. Schema-1/schema-2 migration assertions compile but remain unverified on device.
- Refreshed `releases/RalLedger-debug.apk` from the schema-17 debug build: 23,492,390 bytes; SHA-256 `25ABBB7432F46873DC330FF02C514B273BB057A2A58C33188879AB833C26EADE`. `apksigner` verifies its v2 signature. It is debug-signed, manifest version `1.0`; production signing is not configured.

## Latest GitHub publication — 2026-10-24

- Published the saved custom reports milestone to `main` in `Ralimations/RalLedger-finance-management` as commit `da2928ceb7cac8ba545771379b89c0a37795a33c` (`feat: add saved custom reports`). GitHub confirms `main` points to this commit.
- Updated `releases/RalLedger-debug.apk` to the schema-20 build: 24,091,643 bytes; SHA-256 `19BF52F7F55B282F8D379780389FFC7C3468A0CE5382EBC9B431F5D8B0A9BD94`. The v2 APK signature verifies. It is debug-signed, version `2.0`, and not production-signed. GitHub confirms the APK is present on `main`.
- Verification: 68 unit tests pass; `assembleDebug`, lint (0 errors, 28 warnings), and Android-test Kotlin compilation pass. The connected emulator remained offline, so the new migration and report CRUD/merge/backup instrumentation compiles but remains unverified on device.

## Transaction templates — 2026-10-24

- Added schema 18 saved transaction templates for income, expense, and transfers, including transfer fees, selected accounts/categories, source or counterparty, description, notes, and archive state.
- Templates support case/whitespace-insensitive unique names, create/edit, search, archive/restore, confirmed deletion, and prefilling a standard transaction form. Applying a template never posts automatically; the user reviews and presses Post.
- Account/category references use restrictive foreign keys; deletion guards block removal while templates refer to records, and category merge updates both transaction and transfer-fee category links. Encrypted backups validate and restore templates while older backups default to an empty template list.
- Added instrumentation for template CRUD, normalized duplicate rejection, reference guards, category merge, and encrypted backup/restore, plus schema-1/schema-2 migration coverage through version 18. These instrumentation sources compile; device execution is still pending.
- Final verification passed `:app:assembleDebug`, `:app:testDebugUnitTest` (67 tests, 0 failures), `:app:lintDebug` (28 warnings, 0 errors), and `:app:compileDebugAndroidTestKotlin`. The connected emulator remained offline, so no live-device migration or template workflow is claimed.
- Refreshed `releases/RalLedger-debug.apk` from the verified build (23,504,293 bytes; SHA-256 `BEAE6F1995C18836BEBD29E6FE02C5E8766ADBEEB1F55AEFE4814962E50A66A4`). `apksigner` verifies the v2 signature. It remains a debug-signed APK, version `1.0`, not a production-signed release.

## Planned payments — 2026-10-24

- Added schema 19 one-time planned payments. Creating or editing a forecast has no journal, balance, or budget effect. Posting an actual payment creates a standard expense journal entry at the user-entered actual amount and links it to immutable payment history.
- The Planning area now supports searchable active and archived plans, editing before any payment history, cancel/reactivate, archive/restore, safe deletion only before payment history, recording a payment from an active asset account, and reversing a payment from its history. Reversal keeps both journal and payment events and makes the plan available again; generic correction is blocked for a linked expense so the relationship cannot be lost.
- Category and saved-counterparty foreign keys restrict deletion; category and counterparty merges reassign live plans. Encrypted backup now includes plans and payment/reversal events, validates their journal/account/category links, and restores them with older backups defaulting to empty lists.
- Added schema-1/schema-2 migration coverage through schema 19 and a lifecycle/merge/post/reverse/re-record/archive/backup instrumentation test. Verification passed `:app:testDebugUnitTest` (67 tests, 0 failures/errors/skips), `:app:lintDebug` (28 warnings, 0 errors), `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`. Android-test source compilation confirms the new instrumentation test builds; connected execution is unavailable because both listed emulators are offline and no AVD is configured.
- Refreshed `releases/RalLedger-debug.apk`: 23,869,534 bytes; SHA-256 `9BC96AF3ED7C48F3EDA7FA541CEFE938A6C52A924A7EEA32CF3D2F4EA1F101CB`. `apksigner` verifies the v2 signature; the manifest reports `com.ralledger`, versionCode 2, versionName 2.0. The APK is signed with the Android debug key, not a production key.
- At that checkpoint, project tracking, savings-growth statistics, and personal/business rollups remained open. Custom reports supported transaction-label filters, which could separate labeled activity.

## Schema 20 — Saved custom reports

- Added a version-20 `custom_reports` table with normalized unique names, inclusive date ranges, optional account/category/label filters, archive state, and timestamps. Schema-1 and schema-2 migration fixtures now traverse through version 20.
- Added Planning → Reports with create, edit, search, archive/restore, and confirmed delete. Saved reports calculate income, expenses, net, and category totals from journal-backed activity; account, category, and label filters are reusable. Linked reversals remain associated with the selected source event and are counted on their posting date.
- Account/category/label references use restrictive foreign keys. Permanent deletion is blocked while a report refers to the record; category and label merges update saved report references atomically.
- Encrypted backup/restore now includes saved reports, validates names, date ranges, timestamps and referenced records, and remains compatible with older backups that omit the report list.
- Added a focused unit case for report filtering and reversal-period attribution, plus instrumentation for CRUD, duplicate names, reference protection, merges and encrypted backup round-trip. Verification passed 68 unit tests, `:app:lintDebug` (28 warnings, 0 errors), `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`. The connected instrumentation suite could not run because the two listed devices are offline and no AVD is configured.
- Refreshed `releases/RalLedger-debug.apk` from the schema-20 build: 24,091,643 bytes; SHA-256 `19BF52F7F55B282F8D379780389FFC7C3468A0CE5382EBC9B431F5D8B0A9BD94`. The v2 APK signature verifies; package version remains 2.0 (versionCode 2). The APK uses the Android debug signing key and is not production-signed.

## Financial projects and report filters — 2026-10-24

- Added Room schema 21 for Personal and Business financial projects and one-project-per-journal-entry assignments. Planning now supports project creation, search, editing, archive/restore, same-kind merge, and guarded deletion. Posted transactions can be assigned or moved between projects; reversals inherit their source assignment so journal-backed totals stay consistent.
- Project summaries show all-time income, expenses, net, and category totals. Saved custom reports can filter by project. Transaction CSV exports include project ID and name. Encrypted backup preview, validation, export, and restore now include projects, journal-entry assignments, and report links while older backups default to no projects and no project filter.
- Added project calculator unit coverage plus connected lifecycle coverage for create/edit, duplicate-name rejection, assignment, archive/restore, merge, reference guards, reversal totals, and encrypted-backup round-trip. The Android-test sources compile; connected instrumentation could not run because the available emulator transports are offline and no AVD is configured.
- Verification passed `:app:testDebugUnitTest` (69 tests, 0 failures), `:app:lintDebug` (29 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`.
- Refreshed `releases/RalLedger-debug.apk`: 23,448,469 bytes; SHA-256 `733F50752C90B3760D37F687599A0226E14FA531C9113BF3A2A88E0C5B612680`. `apksigner` verifies the v2 signature. Package version is 2.0 (versionCode 2); the APK is signed with the Android debug key, not a production key.
- Published commit `48d34eac3cfb6850676f48ed97fd031858009265` to `Ralimations/RalLedger-finance-management` on `main`. Git and GitHub both confirm that `main` points to this commit; the GitHub tree confirms the APK path and byte size.
- Remaining checks at that checkpoint included connected schema-1/schema-2 migration and project lifecycle execution, savings-growth analytics, and small-screen release work.

## Personal-versus-business Insights — 2026-10-24

- Insights now separates the currently selected date range into Personal and Business activity based on each assigned project's kind. Each scope shows income, expenses, net, expandable income-source totals, and category spending. Transactions without a project remain visible in a separate Unassigned section and are not silently counted as Personal or Business.
- The portfolio calculator selects source journal entries by project kind and includes each linked reversal on its own posting date. It uses the existing Insights calculator for period boundaries, totals, categories, and income sources; it does not hardcode report values.
- Added unit coverage for project-kind aggregation, an original transaction and its reversal, unassigned activity, category totals, and income sources. Verification passed `:app:testDebugUnitTest` (70 tests, 0 failures), `:app:lintDebug` (29 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`.
- Refreshed `releases/RalLedger-debug.apk`: 23,895,199 bytes; SHA-256 `9EF4BA591B50DB9F98F0141F408468784BE269839E2F76062C8667E138EAB513`. `apksigner` verifies the v2 signature. The APK remains version 2.0 and debug-signed, with no production signing configuration.
- Published commit `b6318f75b400ce89254e258fd52dd8ce944a73c1` to `Ralimations/RalLedger-finance-management` on `main`. GitHub confirms the branch head and APK blob path/size.
- No emulator UI walkthrough was possible because both available emulator transports are offline and no AVD is configured. At this checkpoint, savings-growth analytics, small-screen validation, and connected migration/lifecycle checks remained open.

## Savings-growth history — 2026-10-24

- Insights now charts period-end balances for active physical-savings, time-deposit, and interest-bearing savings accounts. The calculator replays persisted journal lines at the selected period boundaries, so actual deposits, withdrawals, reconciliation adjustments, and credited interest affect the history; it does not use interest estimates or count reserved goal allocations as extra assets.
- The chart supports tap-to-inspect period-end balances and compares the range's end balance with the balance immediately before its first date, so a single-month view reports the full monthly change. Accounts excluded from dashboard totals still appear in savings analytics; archived accounts and ordinary checking accounts without a savings profile do not.
- Added unit coverage for savings and time-deposit accounts, configured interest-bearing accounts, actual interest credit, withdrawal, account archive exclusion, and excluding a non-savings checking account. Verification passed `:app:testDebugUnitTest` (71 tests, 0 failures), `:app:lintDebug` (29 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`.
- Refreshed `releases/RalLedger-debug.apk`: 23,579,113 bytes; SHA-256 `4B3545AB3B9DC4966DF26211147C89C3CCA5D2499065D153AB9F3DF7E1A3C2E5`. `apksigner` verifies the v2 signature. It remains version 2.0, debug-signed, and is not a production build.
- Published commit `cfa98bd525ac003971c32e69ef036f7064bdf537` to `Ralimations/RalLedger-finance-management` on `main`. GitHub confirms the branch head and APK blob path/size.
- The emulator transports remain offline and no AVD is configured, so the new chart has no device walkthrough yet. Small-screen checks, connected migration/project lifecycle tests, backup recovery cases, and the other partial lifecycles in the CRUD matrix remain.

## Durable encrypted-restore recovery — 2026-10-24

- Added Room schema 22 with a DataStore recovery journal containing the prior and requested user preferences plus a Room commit marker. The marker is written in the same transaction as the financial-record replacement.
- If the app restarts before the Room transaction commits, startup restores the previous preferences. If the database committed first, startup applies the backup preferences. The app waits for recovery before showing financial screens or processing subscription reminders.
- A failed preference write attempts immediate recovery; if storage still fails, the durable journal remains for the next launch. Device app-lock remains local and is not part of backup preferences.
- Added instrumentation cases for journal serialization, recovery before/after the database commit, and a preference-write failure. The instrumentation sources compile; execution remains unverified because both available emulator transports are offline and no AVD is configured.
- Verification passed `:app:testDebugUnitTest` (71 tests, 0 failures/errors), `:app:lintDebug` (29 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`.
- Refreshed `releases/RalLedger-debug.apk`: 23,574,552 bytes; SHA-256 `0BB0C3989F5A16EAB5B57E515FF7006D29D178B5E4B069CAC334027CEDC88ECF`. `apksigner` verifies the v2 signature. Package version remains 2.0 (versionCode 2); this APK is debug-signed, not production-signed.
- Published commit `41852c210bae6c75cdb47efb7b36a312b5a8afe4` to `Ralimations/RalLedger-finance-management` on `main` under the user's explicit authorization. GitHub confirms `main` points to this commit and `releases/RalLedger-debug.apk` is present at 23,574,552 bytes.
- Remaining live-use checks include connected migration/recovery execution, screen walkthroughs on small devices, and partial lifecycle items in `FEATURE_CRUD_MATRIX.md`.

## Connected migration and restore verification — 2026-10-24

- Brought the configured `Medium_Phone` Android API 37 emulator online and ran `:app:connectedDebugAndroidTest`; all 34 connected tests passed with 0 failures, errors, or skips. The suite covered CRUD/reversal/backup flows, schema-1 and schema-2 upgrades through schema 22, DataStore journal serialization, pre-commit rollback recovery, post-commit preference completion, and preference-write retry recovery.
- The emulator tests use in-memory databases or named migration fixtures. The recovery-journal serialization test clears its dedicated journal in a `finally` block.
- The existing 71-test unit suite and build/lint verification remain green (29 lint warnings, 0 errors). No application source or APK changed during this verification follow-up; the debug APK remains at `releases/RalLedger-debug.apk`, version 2.0 / versionCode 2, SHA-256 `0BB0C3989F5A16EAB5B57E515FF7006D29D178B5E4B069CAC334027CEDC88ECF`.
- Updated `FEATURE_CRUD_MATRIX.md` to record connected results. GitHub `main` was `07c14d2150e0d9020413bc2f6af4ae2a0350b64a` before this documentation follow-up; UI walkthroughs for the new charts, small-screen sizing, device app-lock behavior, and actual notification delivery remain open.

## Smart financial statement import — 2026-10-24

- Replaced the CSV-only picker path with a local format-aware reader for CSV/TSV, XLSX, OFX/QFX, QIF and PDF. Delimited files support comma, semicolon, tab and pipe detection, UTF-8/UTF-16 BOMs and Windows-1252 fallback. CSV header preambles are skipped when a usable header is found; headerless files retain the first transaction and use generated columns for manual mapping.
- XLSX reads the first visible worksheet with bounded workbook expansion and row/cell limits. OFX/QFX and QIF fields are normalized into the existing transaction mapper; QIF transfers are recognized and split rows are blocked from import until converted. PDF statements use selectable-text extraction, with bundled on-device ML Kit OCR for scanned pages. The selected file stays local and every format goes through the existing preview and explicit confirmation before writing transactions.
- The mapping dialog supports manual column/date-order selection and defaults for account, transfer destination and income/expense categories. Preview validation still requires existing active accounts/categories, enforces currency and transfer constraints, skips duplicates and rechecks duplicates at database write time. Only income, expense and transfer rows are in scope.
- Limits and gaps are explicit: files up to 20 MB, at most 50,000 rows, bounded XLSX expansion/cells, and PDFs up to 100 pages. Legacy `.xls` and password-protected PDFs are rejected. PDF row extraction is best-effort; scanned text and multiple monetary columns need review, and this reader does not import budgets, receivables, debts, subscriptions or other specialized records.
- Verification passed `:app:testDebugUnitTest` (77 tests, 0 failures), `:app:connectedDebugAndroidTest` (37 tests, 0 failures/errors/skips), `:app:lintDebug` (33 warnings, 0 errors), and `:app:assembleDebug`. Device tests cover XLSX, selectable-text PDF and scanned-PDF OCR. The emulator display override was reset to 1080×2400.
- Manual Settings smoke on the 1080×2400 emulator opened Android's document picker and the import-review dialog for a synthetic CSV. Date/type/amount/account/category mappings were suggested; import remained disabled because the sample account was not present in the emulator. The dialog was canceled and the synthetic file removed, so no transaction was written.
- Refreshed `releases/RalLedger-debug.apk`: 75,987,842 bytes; SHA-256 `4951CF6309C81C2A743133D5090187338FA94935520CA01784673E0E59065785`. `apksigner` verifies the v2 signature; package version remains 2.0 (versionCode 2) and the APK is debug-signed, not production-signed.
- At the end of the smart-import pass, those changes were held locally and uncommitted. They are included in the combined release containing the calculator feature documented below. Remaining work includes production signing and broader UX/device review, including import preview behavior with real-world statements from different institutions.

## Money-entry calculator and account balance context — 2026-10-24

- Added a reusable calculator action to monetary add/edit fields across transactions and transfer fees, account opening/reconciliation balances, budgets, receivables, subscriptions, expected income, planned payments, savings goals, debt payments, and actual interest entries. Percentage fields remain standard numeric inputs.
- The calculator supports decimals, addition, subtraction, multiplication, division, parentheses, normal operator precedence, clear and backspace. It uses decimal arithmetic and writes a two-decimal result into the active amount field; it does not submit the form.
- Added a compact account-balance panel to both Quick Add and the Activity transaction entry/correction forms. It shows every active account balance in the form flow and scrolls internally when the account list is long, so it does not float over fields or save controls.
- Verification passed `:app:testDebugUnitTest` (81 tests), `:app:lintDebug` (33 warnings, 0 errors), and `:app:assembleDebug`. Manual 1080×2400 emulator smoke opened Quick Add, calculated `1 + 2 = 3.00`, inserted the result, and canceled without posting. No active test accounts were present, so populated balance-list and small-screen layout review remain open. Connected instrumentation was not rerun for this UI-only change.
- Refreshed `releases/RalLedger-debug.apk`: 76,343,987 bytes; SHA-256 `11BF24398CB33B4F502530C2AA0A8090E97BF8181757796645B8C603DE9C04E1`. The v2 APK signature verifies; package remains `com.ralledger`, version 2.0 / versionCode 2, signed with the Android debug key.
- Published source commit `861984402a0f7860044dd446130177ca16647888` to `Ralimations/RalLedger-finance-management` on `main`, including `releases/RalLedger-debug.apk` (76,343,987 bytes). GitHub accepted the push and warned that the APK exceeds its 50 MiB recommended file size. Production signing and a multi-account small-screen walkthrough remain outstanding.

## Income and expense category availability — 2026-10-24

- Confirmed the category model keeps `INCOME` and `EXPENSE` independent. Fresh databases seed four income categories and eight expense categories; transaction pickers filter to the selected transaction type.
- Opened the installed version 2.0 app on the available 1080×2400 Android emulator and confirmed the Categories manager displays distinct Income and Expenses sections with seeded categories. No financial records or categories were added or changed during this check.
- This is an availability check only. Category subcategory, merge, history, and smaller-screen walkthroughs remain open; the broader release checklist and production signing are still incomplete.

## Current build and connected verification — 2026-10-24

- Using the installed JDK 25 and approved Gradle downloads, ran `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, and `:app:connectedDebugAndroidTest` on `Medium_Phone(AVD) - 17` (Android API 37).
- Results: 81 unit tests and 37 connected Android tests passed with 0 failures, errors, or skips; lint reported 33 warnings and 0 errors; debug assembly and Android-test Kotlin compilation succeeded. Two offline emulator entries were skipped.
- The generated debug APK and `releases/RalLedger-debug.apk` are both 76,343,987 bytes with SHA-256 `11BF24398CB33B4F502530C2AA0A8090E97BF8181757796645B8C603DE9C04E1`. `apksigner` verifies the v2 signature. The APK is signed with the Android debug key, not a production key.
- No application code or APK changed in this verification-only pass. Remaining live-use work includes production signing, outstanding manual flows and responsive-layout checks listed in `FEATURE_CRUD_MATRIX.md`, and review of lint warnings.

## Native home-screen money widget — 2026-10-24

- Added a resizable Android home-screen widget showing the journal-derived Available to spend amount, with Income, Expense, and Transfer shortcuts into Quick Add. The selected shortcut preselects its transaction type; the existing Quick Add account-balance panel stays in the form flow.
- The widget follows the app's currency symbol/code display setting. It hides the amount whenever app lock is enabled and suppresses the amount if startup backup recovery fails. Room-backed dashboard changes, currency display changes, and app-lock changes refresh installed widgets while the app process is active; Android's periodic widget refresh covers later openings.
- Added strict widget-intent validation plus tests for transaction type selection, currency formatting, and balance redaction under app lock or recovery failure. `:app:testDebugUnitTest` passed (84 tests); `:app:lintDebug` completed with 32 warnings and 0 errors; `:app:assembleDebug` and `:app:compileDebugAndroidTestKotlin` succeeded. Removed the widget-specific lint warnings.
- The connected Android test task could not run because ADB reported no connected devices and the only transport was offline; no local AVD was available to start. Therefore launcher installation, widget privacy rendering, responsive layout, and shortcut tap-through still need a device check.
- Refreshed `releases/RalLedger-debug.apk`: 77,928,083 bytes; SHA-256 `ADF1135C7E6BE4F31C4EF4939A6B7A74F2E09E61B3472A065D6794344353CBBB`. It is the version 2.0 debug-signed APK, not production-signed.
- Published commits `019b39e`, `8580b2a`, and `4134fc6` to `Ralimations/RalLedger-finance-management` on `main`; GitHub confirms `main` at `4134fc6` and the APK is present. GitHub warned that the 74.32 MiB APK exceeds its 50 MiB recommended file size. Device walkthrough items remain open.

## Phase II — Unified action entry and theme foundation — 2026-10-25

- Added a shared Material 3 Quick Action Hub on all five primary destinations. Income, Expense, and Transfer open the existing transaction form with the selected type; More groups receivables, expected income, subscriptions, budgets, savings goals, bills, loans, and interest products by intent. Target-dependent collect and interest actions open their existing lists instead of guessing a record.
- Wired planning actions into the existing module editors, so actions open the current form and do not create a record before Save. Quick Add continues to show account balances and now prioritizes the saved eligible account, then the latest eligible non-reversed account for the transaction type, then the first active asset account. When the selected category is no longer eligible, the latest matching category is suggested before the first available one. Valid manual choices are retained.
- Added a Quick Add in-flight save guard. Save disables while repository posting is running; invalid input stays in the form and repeated taps cannot initiate another post during that operation.
- Updated the default Ocean palette to accessible blue and neutral light surfaces; added Coral and Amber palette choices alongside Indigo and Sage. Palette persistence continues using the existing DataStore setting.
- Verification on the final source passed `:app:testDebugUnitTest` (84 tests), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. The generated debug APK is 77,928,083 bytes with SHA-256 `A7910D96BA559283C9F17E883C4BC03CA5BFB6B49EBB6F61F8952B279395DE0F` at `TestApplication/app/build/outputs/apk/debug/app-debug.apk`.
- ADB reported no attached devices, so the action routes, visual layout, keyboard behavior, screen-reader flow, and small-screen fit were not manually walked through. Unsaved-change confirmation, shared form-control extraction, contextual CRUD consistency, and the broader Phase II screen redesign remain in progress. Phase II changes remain local and unpublished.

## Phase II — Planning navigation consolidation — 2026-10-25

- Replaced Planning's eleven horizontally scrolling tabs with a grouped landing screen: Money in and commitments; Budgets and savings; Organization and reports. Each row opens the existing feature screen and its existing add/edit/archive/history controls.
- Added an in-screen return affordance and Android Back handling so module navigation returns to the Planning landing screen instead of exiting the main destination. Quick Action Hub routing can still select a target module and open its existing editor.
- Regression verification on the updated source passed `:app:testDebugUnitTest` (84 tests), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. No Android device was attached for a visual walkthrough.
- Current generated APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes, SHA-256 `CA355919D1338901752CE63E0FB7A1A40A5A99F68D38BB88981F5EFE7AA39ED0`. Phase II changes remain local and unpublished.
- Remaining Phase II work includes shared input components and form presentation, unsaved-change confirmation, contextual CRUD consistency, dashboard and analytics visual hierarchy review, Settings navigation, accessible small-screen/device review, and full cross-flow regression.

## Phase II — Shared monetary input — 2026-10-25

- Added `MoneyAmountField`, a shared Compose input for monetary add/edit forms. It standardizes the decimal keyboard and embedded calculator affordance while keeping per-form labels, enabled state, error-reset callbacks, and save validation.
- Replaced duplicated calculator-enabled amount fields across Quick Add, transaction entry/correction/templates, account opening and reconciliation, budgets, receivables and collections, expected income, planned payments, bills and loans, subscriptions, savings goals, and interest profiles/credits. `MoneyCalculatorAction` is now called through the shared field component rather than copied into each form.
- Verification passed `:app:testDebugUnitTest` (84 tests), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verifies the generated debug APK with v2 signing.
- Current generated APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes, SHA-256 `12F3FE1035C741151552A520076F018B3D7B6A15A2F2C372E120939431EA4086`. No Android device was attached for keyboard or visual QA; this debug APK is not a production-signed release.
- Remaining form work includes shared account/category/date/notes controls, progressive disclosure in the main Quick Add sheet, unsaved-change confirmation, and a complete screen-by-screen layout and accessibility walkthrough. This Phase II iteration remains local and unpublished.

## Phase II — Settings navigation hierarchy — 2026-10-25

- Replaced the single long Settings page with a purpose-based home for Appearance, Preferences, Dashboard, Categories, Data & Backup, Security, and About. Each section opens on demand and returns through both an in-screen back control and Android Back.
- Preserved the existing settings operations in their section: theme and palette; default transaction account, reporting period, date/currency display, transaction density and subscription reminders; dashboard widget visibility/order; category management; statement import/export and encrypted backup/restore; and app lock.
- Verification passed `:app:testDebugUnitTest` (84 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verifies the generated APK with v2 signing. ADB only reports an offline emulator, so the Settings navigation and touch-target layout were not device-tested.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `2A6B256B0AE5A9FE78F325D529088D9E389EC45BE4B62145E2060820CCB9937B`. It remains debug-signed and is not a production release.
- Settings hierarchy is implemented; broader Phase II work remains open, including progressive disclosure and unsaved-change handling in Quick Add, contextual CRUD presentation, dashboard and analytics hierarchy, shared non-amount fields, accessible device walkthroughs, and full cross-flow regression. Phase II changes remain local and unpublished.
