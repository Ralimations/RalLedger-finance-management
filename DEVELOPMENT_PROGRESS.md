# RalLedger v2.0 Development Progress

Updated: 2026-10-27
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
| Transactions | Income, expenses, transfers/fees, refunds, loan proceeds and principal/interest payments, search, type/account/category/date filters, duplicate, reversal, atomic correction by reversal plus replacement, and transaction draft recovery | Attachments are not implemented; linked receivable/debt postings stay in their dedicated correction workflows |
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
- Settings-slice debug APK at that build: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `2A6B256B0AE5A9FE78F325D529088D9E389EC45BE4B62145E2060820CCB9937B`. It was debug-signed, not a production release.
- Settings hierarchy is implemented; broader Phase II work remains open, including progressive disclosure and unsaved-change handling in Quick Add, contextual CRUD presentation, dashboard and analytics hierarchy, shared non-amount fields, accessible device walkthroughs, and full cross-flow regression. Phase II changes remain local and unpublished.

## Phase II — Quick Add form and discard safety — 2026-10-25

- Replaced the transaction `AlertDialog` with a scrollable Material 3 bottom sheet for Expense, Income, and Transfer. It keeps the account-balance summary in the form, labels the amount with the selected account's currency, shows the required account/category or transfer destination, and collapses the optional description until requested.
- Cancel, close, system Back, and outside dismissal route through one policy: an untouched form closes, an edited form asks before discard, and an in-progress save ignores dismissal. The save guard is set synchronously before starting the repository coroutine, preventing repeated taps from starting concurrent posts. Existing record operations and validation remain unchanged; only Save calls the repository.
- Added three unit tests for empty, edited, and saving dismissal states. Verification passed `:app:testDebugUnitTest` (87 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verifies APK v2 signing.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `B00263F02517E037759BB2B622C3F1BBC5C7D84C4D9ABD15F3F09DF963355BA8`. The emulator transport remains offline, so gestures, keyboard resizing, visual balance layout, and screen-reader behavior were not device-tested. The APK is debug-signed, not a production release.
- This completes unsaved-change protection for the primary Quick Add transaction form only. Guard coverage for other add/edit forms, shared non-amount controls, dashboard/analytics hierarchy, device accessibility walkthroughs, and full cross-flow regression remain open. Phase II changes remain local and unpublished.

## Phase II — Dashboard hierarchy and legacy preferences — 2026-10-25

- Added a configurable Recent Transactions card sourced from the existing journal. It shows the latest five eligible entries, excluding reversed originals, reversal rows, opening balances, and reconciliation adjustments; “See all” opens the Transactions destination.
- Improved dashboard hierarchy and narrow layouts: clarified that Available Money is after reserved savings, stacked the money-owed cards below 360 dp, and corrected Net Worth sublabel contrast on its neutral card.
- Kept existing user preferences intact across the new seventh card. Old six-card DataStore order/visibility and encrypted backup lists append Recent Transactions as visible while retaining saved widget order and visibility; migration helpers have unit coverage and the Android backup-migration test compiles.
- Verification passed `:app:testDebugUnitTest` (92 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified the APK v2 signature. The emulator is offline and no AVD is configured, so responsive UI and actual backup-restore migration were not device-tested.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `FFBAE72E617618F122AC7B9796651B8BAD536E6883F7420B92F6C95A43A3D1A5`. It is debug-signed, not a production release.
- Dashboard hierarchy is implemented locally. Real attention items, full Insights hierarchy, remaining specialized-form discard guards, shared non-amount controls, accessible device walkthroughs, and end-to-end cross-flow regression remain open. Phase II changes remain local and unpublished.

## Phase II — Activity journal and transaction details — 2026-10-25

- Grouped the transaction journal by local calendar day, newest first. Income and expense retain semantic signs; transfers remain distinct; opening balances are labeled as setup activity; reversals and reversed originals are identified.
- Kept All, Income, Expense, and Transfer chips visible under search. Moved account, category, label, date, Other, and saved-filter controls into a secondary bottom sheet while preserving existing saved-filter operations.
- Made each transaction row open a scrollable detail sheet with stored type, amount, account/destination, categories, date/time, counterparty, notes, labels, project, journal ID, audit state, and linked reversal entries where the journal stores a reversal reference. Added the detail/history entry to each row's More menu and exposed Correct, Duplicate, Reverse, and related management actions from the detail view.
- Avoided mockup-only location, attachment, and external reference fields because the transaction model does not store them. Financial corrections and reversals still call the existing repository operations.
- Verification passed `:app:testDebugUnitTest` (95 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified the APK v2 signature. No Android emulator is online and no AVD is configured, so gesture, font scaling, and TalkBack review remain pending.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `48AA4A6B574BF03C9A92764E80F972CB28E72F9886352B5593A26D36F1C6E6B1`. It is debug-signed, not a production release.
- Account detail hierarchy, full Insights simplification, attention items, broader specialized-form discard guards, non-amount shared controls, and cross-flow device regression remain open. Phase II changes remain local and unpublished.

## Phase II — Account detail and contextual actions — 2026-10-25

- Made account cards open a focused detail sheet showing account type, institution, ledger balance, reconciliation date, dashboard inclusion, description, and the latest five account transactions. The full history remains one tap away.
- Added contextual Receive and Transfer actions for eligible active asset accounts. They open existing Quick Add flows with the selected account preselected. Account selection prioritizes this context, then the user's saved default, then the latest eligible account used for that transaction type, then the first eligible account; invalid/archived selections safely fall back.
- Kept Edit, Archive/Restore, guarded Delete, and Reconcile available from the detail sheet and existing account menu. Transfer history signs now reflect whether the selected account was the source or destination.
- The account model does not assign goal reserves to individual accounts. The detail view therefore reports the real ledger balance and explains that Home's Available Money is aggregate rather than displaying a fabricated per-account available figure.
- Verification passed `:app:testDebugUnitTest` (101 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified v2 signing. ADB reports emulator-5562 offline and no AVD is configured, so account detail, contextual selection, and small-screen layout await an online device.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `4FE1BC2BC9114712309911233CCFF51D4EF539A891E373DA0ECF523791BFB2C5`. It is debug-signed, not a production release.
- Account-level available balance attribution, full detail layout QA, Insights hierarchy, attention items, remaining specialized-form discard guards, and end-to-end device regression remain open. Phase II changes remain local and unpublished.

## Phase II — Insights hierarchy and period control — 2026-10-25

- Kept Day, Week, Month, and Year as the primary period chips. Custom Range and All Time now appear under More; the existing previous/next and inclusive date-range behavior is retained.
- Put the period Income/Expenses/Net summary and selectable income-versus-expenses chart first. Grouped the existing secondary reports under Personal and Business, Balances and Savings, Interest Earnings, Spending by Category, Income Sources, Account Distribution, and Commitments and Receivables headings.
- Kept all existing journal-backed reporting calculations and chart data unchanged, including period summaries, categories, sources, account balances, net worth and liquid-asset history, savings growth, interest, subscription costs, and receivable aging.
- Verification passed `:app:testDebugUnitTest` (101 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified v2 signing. ADB reports emulator-5562 offline and no AVD is configured, so period-menu, chart-selection, text-scaling, and small-screen review remain pending.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `6666907E43AFD27B676F18811A726FA455294133EF0B6F3618DA59D6941579CA`. It is debug-signed, not a production release.
- Phase II changes remain local and unpublished. Attention items, full on-device report walkthrough, and broader cross-flow regression remain open.

## Phase II — Contextual dashboard attention — 2026-10-25

- Added a Home attention panel from existing planning flows: overdue bills and receivables, planned payments due within seven days, active subscription renewals due within three days, and current-month budget overruns.
- Sorted overdue items first, then budget overruns, near-term commitments, and renewals. Settled, cancelled, inactive, archived, out-of-window, and non-current budget entries are excluded. Selecting an item opens its Planning module; the header opens the Planning hub. Currency and date formatting use the user's current display preferences.
- Added deterministic unit coverage for item status filtering, date windows, priority order, and over-budget amounts.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. ADB reports emulator-5562 offline and no AVD is configured, so visual and interaction review remains pending.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `E1A1BAD5F0EA052BAB4B1B527FE86D69BBB79B464F06922A0C6FFEA35EEED287`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Specialized-form discard guards, shared non-amount controls, accessible device walkthroughs, and full cross-flow regression remain open.

## Phase II — Planning editor discard protection — 2026-10-25

- Added a shared confirmation dialog for discarding unsaved changes.
- Budget editors detect changes to category, amount, or month. Savings Goal editors detect changes to name, target, target date, or notes. Cancel, system Back, and outside dismissal close an untouched form directly; edited forms require Keep editing or Discard.
- Existing Save operations and repository validation remain unchanged. Discard guards for other specialized editors are still open.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. The emulator is offline and no AVD is configured, so dialog layering and Back/outside interaction remain device-review items.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `0BEAC65F84C37F36B2F4A01F9A092F719D6E40E1703CC1419D896697089AD256`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Remaining Phase II work includes guards on other specialized forms, shared non-amount controls, accessible device walkthroughs, and full cross-flow regression.

## Phase II — Core commitment editor discard protection — 2026-10-25

- Extended the shared discard confirmation to Receivable, Expected Income, Planned Payment, Bill/Loan, and Subscription editors.
- The guard compares all editable values—including date, amount, account/category, and linked contact fields—to the values loaded when the editor opened. Untouched forms close directly; changed forms offer Keep editing or Discard. Existing save and posting callbacks are unchanged.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. No Android emulator is online and no AVD is configured, so dialogs need a device walkthrough.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `FB00BD37FB6E31F26F3F1F3E4AA20C658FEF9FEC350CA13994ABDF1E05440A55`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Discard guards for the remaining secondary editors, shared non-amount controls, accessible device walkthroughs, and full cross-flow regression are still open.

## Phase II — Account, category, and template editor discard protection — 2026-10-25

- Added changed-draft confirmation to Account, Category, Transaction Template, and Label editors. Account snapshots include the opening balance only when creating a new account; editing an existing ledger account still cannot alter its opening balance.
- Template guards cover transaction type, calculator-enabled amount and fee, source/destination accounts, categories, counterparty, description, and notes. Existing save operations remain unchanged.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. UI review remains blocked by the offline emulator and absent AVD.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `1B6C75E81BB9910AE81F7C206F27BE9AB4929A0358A66E77D638CEB9A03DD9CF`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Payment, transaction-correction, and other dismissible operational dialogs still need unsaved-change review; accessible device walkthroughs and full cross-flow regression remain open.

## Phase II — Secondary Planning editor discard protection — 2026-10-25

- Added changed-draft confirmation to Counterparty, Financial Project, Custom Report, Interest Profile, and Interest Credit editors.
- Tracked the editable fields for each form, including report date/filter selections and interest terms, principal, categories, date, and notes. Dismissal preserves the draft until the user selects Discard; interest posts continue to require the existing explicit save action.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. UI interaction review remains unavailable because the emulator is offline and no AVD is configured.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `48B5424BCF69F2E07D99F8F369F693D6D8CE1B1CF7E76A51DB03F22B361C463D`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Account, category, transaction-template, and payment dialogs still need discard review; shared non-amount controls, device accessibility walkthroughs, and full cross-flow regression remain open.

## Phase II — Operational dialog discard protection — 2026-10-25

- Added changed-draft confirmation to transaction entry, duplication and correction; account reconciliation; receivable collection and write-off; planned, subscription and debt payments; savings allocations; and category/project assignment and merge dialogs. Draft checks include amount, date, account/category selection, descriptions, counterparties, notes, transfer details and selected assignments as applicable. Automatically loaded transaction defaults do not count as user edits.
- Existing Save/Post operations and accounting callbacks are unchanged. Untouched dialogs close directly; changed drafts require Keep editing or Discard.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. The connected emulator remains offline (`emulator-5562`); no interactive device review was possible.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `548B95D2970ECF8B932B8C0B7142F72F75DBF21CC0A1E40E8A23FA29FAB273F3`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Device walkthroughs, accessibility review and full cross-flow regression remain open.

## Phase II — Shared financial form controls — 2026-10-25

- Added reusable account/category selectors, a compact date action, and shared description and notes fields in `ui/components/FinancialFormFields.kt`. The selectors use the eligible account/category lists supplied by each screen; date-picker ownership and timezone conversion remain with the calling screen.
- Adopted the controls in transaction entry/correction, receivable details and collection/write-off, subscription setup/payment, planned-payment setup/recording, and debt setup/payment. Existing journal operations and validations are unchanged. `MoneyAmountField` remains the shared amount/calculator control.
- Verification passed `:app:testDebugUnitTest` (103 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing. The connected emulator remains offline (`emulator-5562`), preventing interactive UI review.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `51ABDE6DF5999F41A6CD0253613E53AD8F5A2D11F2C67119C871A8D5702844BC`. It is debug-signed, not a production release.
- This iteration remains local and unpublished. Shared control adoption in the remaining forms, device/accessibility walkthroughs, and full cross-flow regression remain open.

## Phase II — Safer financial statement import — 2026-10-25

- Updated duplicate handling to distinguish transactions already in the journal from identical-looking rows in the currently selected file. Existing journal matches remain skipped and are rechecked during posting. Repeated in-file rows remain importable and the preview identifies the earlier matching line, while warning that overlapping statement exports can contain repeated activity.
- Kept repeated rows within one confirmed import batch from being mistaken for previously saved transactions by the repository's per-row duplicate recheck. Re-importing those rows later still matches the journal and skips them.
- Added generation checks to file inspection and editable mapping previews. Results from older file/mapping requests cannot replace the current preview; mapping edits are ignored while an import is running.
- Added unit coverage for a repeated statement row and extended instrumentation coverage to verify both repeated rows post as balanced entries and a later re-import skips all saved entries. The instrumentation source compiles; connected execution was unavailable because no device is online.
- Verification passed `:app:testDebugUnitTest` (104 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `060248B2F8E9E11C8ECB4A8D795B185B4CA2B4D67C81AE0018467F1263DCCAC9`. This is debug-signed, not a production release.
- This iteration remains local and unpublished. Import format limitations and the remaining Phase II device/accessibility walkthroughs are still tracked in `FEATURE_CRUD_MATRIX.md`.

## Phase II — API 37 visual validation and system-bar contrast — 2026-10-26

- Created a disposable project-local API 37 x86_64 emulator in `.ralledger-local/avd` using the already-installed Android system image. No real financial data was used.
- Ran the full connected Android-test suite on `emulator-5554`: 38 tests, 0 failures. This includes import preview/parser, repeated in-file statement rows, duplicate re-import protection, balanced posting, CRUD, backup, and migration coverage.
- Inspected Home, Universal Quick Action Hub, and Add Expense at 1080×2400 (about 411×914 dp), compact 720×1600 at 320 dpi (360×800 dp), and large 1600×3200 at 420 dpi (about 609×1219 dp). Checked light and dark mode. Compact Home stacks the owed-money summaries; the large Home keeps its summaries readable.
- Fixed Android 15+ edge-to-edge status and navigation icon contrast by selecting light/dark system-bar icons from the active app theme. Rechecked the light Home screenshot and confirmed the status icons are visible.
- Verification passed `:app:testDebugUnitTest` (104 tests, 0 failures), `:app:connectedDebugAndroidTest` (38 tests, 0 failures), `:app:lintDebug` (32 warnings, 0 errors), `:app:assembleDebug`, and `:app:compileDebugAndroidTestKotlin`. `apksigner` verified APK v2 signing.
- Visual screenshots are in ignored local path `.ralledger-local/qa/`; they are not included in Git. This run visually checked the Home, Quick Action Hub, and Expense form; the full 17-journey manual walkthrough, accessibility/TalkBack review, and broader module-by-module UI coverage remain open.
- Current debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,928,083 bytes; SHA-256 `0A20AF3EEBFF2A9E37412ADBD2549EBAB923F092ADCD9CD81F829098A29B79F1`. It is debug-signed, not a production release. Changes remain local and unpublished.

## Phase II — API 37 synthetic-data walkthrough — 2026-10-26

- On the disposable API 37 emulator, manually checked account creation with an opening balance, income posting, a calculator expression (`12 + 3 = 15.00`) used for an expense, account-to-account transfer, account rename, archive and restore, and an expense reversal. The reversal retained the original journal entry and restored the account balance.
- Checked a receivable and partial collection (₱2,500 recognized; ₱500 collected; ₱2,000 outstanding), subscription creation and edit, monthly budget creation, and savings-goal allocation/release. These are interaction checks, not claims that every edit/archive/delete branch was manually exercised.
- Opened Insights period controls and the custom-range date picker; verified Settings → Categories presents separate Income and Expenses lists; inspected Dashboard widget visibility and reorder controls; and switched to Dark theme.
- Created an encrypted backup from synthetic records, reviewed its restore preview, restored it, force-stopped and reopened the app, and confirmed balances, reserves, receivable outstanding and account records persisted. The AVD data and its test backup file were cleared after screenshots were saved.
- The walkthrough used only generated test records. Screenshots and UI hierarchy captures remain in ignored `.ralledger-local/qa/`.
- Refreshed `releases/RalLedger-debug.apk` from the verified build: 77,928,083 bytes; SHA-256 `0A20AF3EEBFF2A9E37412ADBD2549EBAB923F092ADCD9CD81F829098A29B79F1`. APK v2 signature verification passed. This is a debug-signed test APK, not a production-signed release.
- Verification: 104 unit tests and 38 connected Android tests passed; debug lint reported 32 warnings and 0 errors; debug assembly and Android-test Kotlin compilation passed. App-lock credentials, TalkBack/accessibility, text scaling, manual import/export and complete module-by-module visual review remain open.

## 2026-10-26 — App-lock gate instrumentation

- Added an API 37 instrumentation test that enables app lock on an emulator without a configured credential, launches `MainActivity`, checks the visible lock-gate text through Android accessibility nodes, and asserts that the activity sets `WindowManager.LayoutParams.FLAG_SECURE`. The test restores the preference and closes the activity in `finally`.
- Replaced the Compose test rule after Espresso 3.6.1 failed on API 37 while reflecting `InputManager.getInstance()`. The new test uses `ActivityScenario` and platform accessibility APIs, avoiding that incompatible Espresso synchronization path.
- On the disposable API 37 emulator, separately set a synthetic Android screen credential, enabled app lock through the system prompt, sent the app to the background and reopened it, confirmed that the unlock prompt appeared before financial screens, authenticated, and disabled the lock. The credential was cleared after the walkthrough; no personal financial data was used.
- Verification: 104 unit tests passed; all 39 connected API 37 instrumentation tests passed; `:app:lintDebug` completed with 32 warnings and 0 errors; `:app:assembleDebug` completed. The other attached emulator (`emulator-5562`) was offline and skipped.
- This test-only iteration did not refresh or publish the APK. Remaining device review includes TalkBack/accessibility, text scaling, manual file-provider import/export, dashboard-preference persistence, and broader screen review. The available APK remains debug-signed.

## 2026-10-26 — Large-text navigation fit and semantics

- A live API 37 hierarchy/screenshot audit found the “Accounts” bottom-tab label clipped to “Accou…” at 360dp width and 1.3x system text scale.
- Added an adaptive visual label (“Account”) only at widths of 360dp or less with text scale at least 1.2x. Other widths keep “Accounts”. The tab's merged accessibility semantics retain the full “Accounts” destination name.
- Added unit coverage for the narrow-screen threshold, width and font-scale boundaries, and unchanged labels. Added an instrumentation assertion that TalkBack-visible semantics expose the full “Accounts” name.
- Rechecked the updated Home at 360dp / 1.3x: the full visible “Account” label fits, the accessibility hierarchy contains the “Accounts” name, and emulator display/font settings were restored to 1080×2400 / 1.0x afterward.
- Verification: 107 unit tests passed; all 40 connected API 37 instrumentation tests passed; lint completed with 32 warnings and 0 errors; `:app:assembleDebug` completed. `emulator-5562` remained offline and was skipped.
- Refreshed `releases/RalLedger-debug.apk` locally: 76,372,807 bytes; SHA-256 `2D4B657BC6D2B0DE68C6B75228B813F2E755A30474224A01AA40DB6B592FBFAD`. APK signature scheme v2 verified with the Android debug key. This APK has not been pushed or published and is not production-signed.
- Screenshots and the accessibility dump are in ignored `.ralledger-local/qa/`. Broader TalkBack review across financial forms and screens, text scaling on remaining modules, manual import/export, and dashboard-preference persistence remain open.

## Phase II — Accessible account and category toggles — 2026-10-26

- Made the full “Show archived accounts”, “Show archived”, and transaction-tag assignment rows toggleable, so the entire labeled row is an interaction target and the nested Checkbox is not announced as a second control.
- Moved “Include in dashboard totals” directly after the account’s balance fields, before icon and color customization. Constrained the editor before applying vertical scrolling so the settings and customization controls remain reachable in the dialog.
- Verified on API 37 with a synthetic/empty account list: the account editor exposes the named include-in-totals control, its scroll area reaches the icon and color selectors, and the Categories screen exposes the named Show archived control. Transaction-tag row changes compiled; no tag-assignment dialog was opened during this walkthrough.
- Verification: 107 unit tests passed; all 40 connected API 37 instrumentation tests passed; debug lint reported 32 warnings and 0 errors; debug assembly passed. The offline `emulator-5562` was skipped.
- Refreshed local `releases/RalLedger-debug.apk`: 76,768,713 bytes; SHA-256 `B51A01E5095B7A5E71B4DFF36D5DAE72A440C97F9CF321414BB73619A50575BA`. APK Signature Scheme v2 verification passed; the artifact is debug-signed, not a production release.
- This iteration remains on the feature branch and unpublished. Full TalkBack focus review, accessibility checks across remaining screens, text scaling in other modules, manual file-provider import/export, and dashboard-preference persistence remain open.

## Phase II — CSV import row review — 2026-10-26

- Completed the import-review gap found during release auditing: the mapping dialog now previews parsed transactions, five rows at a time, before the user confirms import. Each row shows its date, type and amount, description, account or transfer destination, category, fee, counterparty, notes, and labels when present.
- Rows are labeled ready, already saved and skipped, repeated within the selected file, or needing attention. The preview includes paging and the action accurately says “Import 1 row” or “Import N rows”; import stays disabled when no valid rows remain.
- On a disposable API 37 emulator, selected a synthetic CSV through Android’s file picker, reviewed the row, imported it, and confirmed the app reported 1 added and 0 duplicates. The synthetic QA wallet changed from ₱10,000.00 to ₱9,850.00.
- Reopened the same CSV and confirmed its row appeared as “Already saved · skipped,” the summary showed 1 saved duplicate and 0 importable rows, and the action was “Import 0 rows.” Canceled the second import and confirmed the wallet remained ₱9,850.00. No real financial data was used.
- Verification on the current source: 107 unit tests passed; all 40 connected API 37 instrumentation tests passed; debug lint reported 32 warnings and 0 errors; debug assembly succeeded. APK Signature Scheme v2 verification passed.
- Refreshed releases/RalLedger-debug.apk: 76,768,713 bytes; SHA-256 7511855AFDD43DE3D8CB5CE173B2163EED47AE45CA52467E6F3C7557D7914CE9. It is debug-signed, not a production release.
- Import review is validated for CSV/TSV, XLSX, OFX/QFX, and best-effort PDF text/OCR through the smart reader; preview mapping remains subject to statement-layout quality. Full TalkBack review and remaining Phase II screen coverage are still outstanding. Widget visibility persistence was verified on API 37 across force-stop/relaunch; reordered-layout persistence remains pending. This branch remains local and unpublished.

## Phase II — Dashboard preference persistence check — 2026-10-26

- On the disposable API 37 emulator, confirmed all seven Home widgets began enabled. Turned off Recent transactions in Settings → Dashboard, force-stopped and reopened RalLedger, and confirmed that visibility setting remained off.
- Restored Recent transactions to visible, force-stopped and reopened again, and confirmed all seven widget controls were enabled. No financial records changed. Reorder persistence was not part of this check.
- Screenshot and UI-hierarchy evidence are stored in ignored .ralledger-local/qa/dashboard-prefs-before.png, dashboard-prefs-before.xml, dashboard-prefs-relaunch.xml, and post-relaunch-current.xml.
- This was a manual persistence check; source code did not change and the automated suite was not rerun in this slice. Latest source verification remains 107 unit tests, 40 API 37 instrumentation tests, and lint with 32 warnings and 0 errors.

## Phase II — Dashboard accessibility labels — 2026-10-26

- Added accessible names to the app-bar Quick actions and Settings buttons and to each Settings section card. The Dashboard widget Switch now carries its own title and detail description, while the text and reorder controls remain separate accessible elements.
- Added an API 37 instrumentation check that navigates Settings → Dashboard and confirms “Recent transactions” is exposed on a checkable node. The test activates a labeled text node through its clickable card ancestor where Android exposes the label and action separately.
- Verification: 107 unit tests passed; all 41 API 37 connected instrumentation tests passed; lint reported 32 warnings and 0 errors; debug APK assembly succeeded. `emulator-5562` remained offline and was skipped.
- Refreshed `releases/RalLedger-debug.apk`: 76,768,527 bytes; SHA-256 `683A76CF256A4D60D69A7B9658C2E3E545EFE03F12E95068FFD1748B2FD92C76`. APK Signature Scheme v2 verification passed; the APK is debug-signed, not production-signed.
- No real financial data was used. Full TalkBack focus review across the remaining screens, other-module text scaling, reordered-widget persistence, manual file-provider import/export, and broad screen-by-screen UI review remain open. This update is local and unpublished.

## Phase II — Dashboard widget-order persistence — 2026-10-26

- Added API 37 instrumentation for moving a dashboard widget and verifying that the saved order returns after Activity recreation. The test captures and restores the emulator's original dashboard preferences.
- Manually verified a real process restart on `emulator-5554`: moved Available Money below Net Worth, force-stopped and reopened RalLedger, and confirmed Settings → Dashboard retained `Net Worth, Available Money` at the top. Restored the original order afterward; the app had no financial records. The post-relaunch screenshot is in ignored `.ralledger-local/qa/dashboard-order-after-force-stop.png`.
- Verification: 107 unit tests and all 42 connected API 37 instrumentation tests passed; lint reported 32 warnings and 0 errors; debug assembly was up to date and APK Signature Scheme v2 verification passed. `emulator-5562` remained offline and was skipped.
- The existing local debug APK remains 76,768,527 bytes with SHA-256 `683A76CF256A4D60D69A7B9658C2E3E545EFE03F12E95068FFD1748B2FD92C76`; no app production source changed in this test-focused slice. It is debug-signed, not production-signed.
- Full TalkBack review, remaining-screen accessibility, other-module text scaling, manual file-provider import/export, and broad screen-by-screen review remain open. This update is local and unpublished.

## Phase II — Responsive Home and Settings checkpoint — 2026-10-26

- Visually checked Home and Settings on API 37 at 320dp (840×1860), 411dp (1080×2400), and 480dp (1260×2800), with 420dpi density. Checked light mode at all three sizes and dark mode at 480dp for both screens.
- At 320dp, content remains vertically scrollable below the fold; visible labels and cards do not overlap bottom navigation. At 480dp, cards and summary rows use the available width without horizontal clipping. Large-screen dark mode retains readable text and status colors.
- Screenshots are in ignored `.ralledger-local/qa/`: `home-compact-light.png`, `home-typical-light.png`, `home-large-light.png`, `home-large-dark.png`, `settings-compact-light.png`, `settings-large-light.png`, and `settings-large-dark.png`. The emulator was restored to 1080×2400, 420dpi, light mode.
- No financial records were present; app source and APK were unchanged. Latest regression is 107 unit tests and 42 connected API 37 tests passed; lint reported 32 warnings and 0 errors; the debug APK remains v2-verified.
- This checkpoint covers Home and Settings only. Other modules, larger text scales, full TalkBack focus, and manual file-provider flows still need review.

## Phase II — Typical-width primary-screen visual sweep — 2026-10-26

- Inspected empty-data API 37 screenshots for Accounts, Activity, Planning, and Insights at 411dp (1080×2400, 420dpi) in light mode.
- The Accounts search and archived filter fit above the empty-state message; Activity search, filter, and type chips fit without colliding; Planning and Insights continue vertically while the bottom navigation remains visible. No visible clipping or overlap was found in the captured viewports.
- Screenshots are retained under ignored `.ralledger-local/qa/`: `accounts-typical-light.png`, `activity-typical-light.png`, `planning-typical-light.png`, and `insights-typical-light.png`. The emulator was restored to 1080×2400, 420dpi, light mode.
- This is empty-data visual inspection at one width only. Populated states, compact and large widths, dark mode on these modules, larger text, and TalkBack remain unverified. No source code changed and the APK was not rebuilt; latest source verification remains 107 unit tests and 42 connected API 37 tests passed, with lint reporting 32 warnings and 0 errors.

## Phase II — Android 17 target and release verification — 2026-10-26

- Raised `compileSdk` and `targetSdk` to 37 while retaining `minSdk` 26. This exceeds Google Play's current API 36 submission minimum and lets this build exercise Android 17 behavior directly; see [Google Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk).
- Fresh verification passed: 107 unit tests; all 42 connected API 37 tests; debug lint with 30 warnings and 0 errors; debug assembly; and Android-test Kotlin compilation. The connected suite includes schema-1/schema-2-to-22 migrations, financial-integrity operations, encrypted restore, app-lock gating, and dashboard accessibility/persistence. The offline `emulator-5562` was skipped.
- Installed the API 37 debug APK and confirmed RalLedger launched. At 610dp width, the Home cards and money summaries remained within the viewport without visible clipping. Screenshot: ignored `.ralledger-local/qa/api37-large-home.png`. Emulator size and density were restored to 1080×2400 and 420dpi.
- Refreshed `releases/RalLedger-debug.apk`: 76,552,681 bytes; SHA-256 `4AE356F048560084455498B051700C02B15F25F154F96487895C1387199C014C`. Manifest inspection confirms package `com.ralledger`, version 2.0 / code 2, min SDK 26, compile SDK 37, target SDK 37. APK Signature Scheme v2 verifies; this remains a debug-signed build.
- The build has no production signing configuration/key, and the release variant has minification disabled. Full TalkBack review, remaining screen-width/theme combinations and text scaling, all 17 manual journeys, notification delivery, and broader file-provider import/export review remain release gates. No push, merge, or publication was performed.

## Phase II - exported receiver action validation - 2026-10-26

- Restricted the exported subscription-reminder reschedule receiver to the four system actions declared by its manifest: boot completed, time changed, timezone changed, and date changed. It now ignores missing or unrelated actions before starting asynchronous database and scheduler work.
- Added unit coverage for all four accepted actions and for null/untrusted actions. Fresh verification passed: 109 unit tests, 42 connected API 37 instrumentation tests, debug lint with 29 warnings and 0 errors, debug assembly, and Android-test Kotlin compilation. The UnsafeProtectedBroadcastReceiver lint warning is gone; `emulator-5562` remained offline.
- Refreshed `releases/RalLedger-debug.apk`: 76,543,542 bytes; SHA-256 `F7D6B49F1DF5EAF2C5456DCB4858AF4B96C7CA1ADDAEBDF6957072B4D4D73FE3`. Manifest reports package `com.ralledger`, version 2.0/code 2, min SDK 26, compile/target SDK 37; APK v2 signature verifies. This is a debug-signed artifact, not a production release.
- Production signing, release minification, full TalkBack and large-text review, all 17 manual journeys, populated-state/theme/width coverage, and remaining notification/file-provider checks remain open. Commit `66171b1` and the tracked APK were pushed to `origin/main` in the public repository.

## Phase II — Journal filter responsiveness and saved-view persistence — 2026-10-26

- Replaced horizontal scrolling in the Activity type chips and secondary filter controls with wrapping `FlowRow` layouts. Account, category, label, and Other controls now wrap within the phone width; the filter sheet remains vertically scrollable for date and saved-view actions.
- On API 37, reviewed the Activity filter sheet at 411dp and 320dp in light mode at 1.0x text. All four primary transaction types wrap across two rows at 320dp; Account, Category, and Label remain fully visible without horizontal panning.
- At 320dp with 1.3x system text, the Activity type chips still wrap, the Categories/Labels/Templates actions move onto complete rows, and the bottom navigation shows compact visible labels (`Acct`, `Plan`, `Stats`) while exposing full destination names to accessibility. Account, Category, Label, and date controls remain inside the filter sheet width; the sheet scrolls vertically for actions below the fold.
- Using synthetic data, created QAWallet with a ₱1,000 opening balance, posted a ₱150 Coffee expense, assigned the QA label, and saved `QA journal` with account/category/label and one-day date criteria. After force-stop and relaunch, the wallet balance was ₱850, both journal rows remained, and applying the saved filter returned only Coffee. Opening-balance setup remained separate from expenses.
- Regression run after the responsive changes: 110 unit tests and all 42 connected API 37 instrumentation tests passed; lint reported 29 warnings and 0 errors; debug APK assembly succeeded. `emulator-5562` was offline. The connected suite left the available emulator on an empty profile; display was restored to 1080×2400 at 420dpi and 1.0x text.
- Refreshed `releases/RalLedger-debug.apk`: 76,759,875 bytes; SHA-256 `F1ADF1D0DD58D929641FF0B2E1FECB8C6F10C6867C94BD1856B8CB9312C8B0B4`. APK Signature Scheme v2 verification passed; the APK remains debug-signed.
- Screenshots are in ignored `.ralledger-local/qa/phase2-filter-sheet-411.png`, `phase2-activity-320.png`, `phase2-filter-sheet-320.png`, `phase2-activity-320-text-130.png`, `phase2-filter-sheet-320-text-130.png`, and `phase2-saved-filter-reapplied-after-restart.png`; they are not committed.
- At 320dp and 1.3x system text on API 37, opened the saved-filter manager with four synthetic views. The dialog title wrapped, the list scrolled vertically, each Apply/Update/Rename/Delete row remained legible, and the Done control stayed outside the scroll area. The rename form also fit at this size.
- Manually created, updated, renamed, and deleted synthetic saved filters. Reopening the filter sheet after Update displayed “Saved filter updated from current filters.” All temporary saved views were deleted; no financial transactions were created. The previously verified Apply and process-restart persistence flow at 411dp remains covered by the prior checkpoint above.
- Review screenshots are in ignored `.ralledger-local/qa/phase2-saved-filters-320-font130-multi.png`, `phase2-saved-filters-320-font130-bottom2.png`, `phase2-saved-filter-rename-320-font130.png`, and `phase2-saved-filter-update-320-font130.png`; they are not committed.
- Emulator display was restored to 1080×2400 at 420dpi with 1.0x text. The compact-width saved-filter manager gate is complete; full TalkBack, the remaining Phase II device journeys, and other release gates remain open.

## Phase II — Settings TalkBack spot check — 2026-10-26

- Enabled TalkBack temporarily on API 37 and inspected Settings → Appearance, Data & Backup, and Security. Settings destinations exposed descriptive accessible names; appearance mode and palette choices appeared as focusable, clickable options, with the selected choices reported as checked.
- Data & Backup exposed labeled Import financial file, Export Transactions, Save Encrypted Backup, and Restore from Backup actions. Security exposed a labeled App lock switch and explained that Android screen lock must be configured before app lock can be enabled. No backup, restore, import, export, or security setting was changed.
- Restored the emulator accessibility service to its original disabled state. This was a read-only spot check of three settings screens; TalkBack focus order, form controls, remaining modules, and the full journey checklist still need review.
- No app source changed, automated tests were not rerun, and the APK was not rebuilt. Latest source verification remains 110 unit tests and 42 connected API 37 tests passed, lint at 29 warnings / 0 errors, and successful debug APK assembly. The available APK remains debug-signed.

## Phase II — API 37 file-provider import/export and duplicate review — 2026-10-26

- Started from an empty API 37 emulator profile and used a synthetic QA CSV. Created a QA wallet with a ₱1,000 opening balance, imported one ₱150 Food & Dining expense through Android's document picker, and confirmed “1 added, 0 duplicates skipped”; the account balance became ₱850.
- Re-imported the same file. The row preview showed “Ready 0 · Saved duplicates 1,” identified the expense as “Already saved · skipped,” and left the action at “Import 0 rows.” Canceled the preview and confirmed the account balance remained ₱850.
- Exported through Android's CreateDocument picker. The app reported two journal entries, and the saved CSV was readable: it contained the opening-balance setup as `OPENING_BALANCE` and the separate ₱150 expense with the QA account, Food & Dining category, and synthetic description. A second export from the initially empty journal had also produced a valid header-only CSV.
- Deleted only the two exports created during this walkthrough. Since the emulator began with no account or transaction records and only this synthetic wallet and expense were added, reset the app profile afterward and verified both Accounts and Activity were empty. Downloads still contains the pre-existing QA input and export files. Emulator display remains 1080×2400 at 420dpi, font scale 1.0, and TalkBack disabled.
- No real financial data was used. App source, tests, and APK did not change in this verification slice. Latest source verification remains 110 unit tests and 42 connected API 37 tests passed, lint at 29 warnings / 0 errors, and successful debug assembly; the APK remains debug-signed. PDF statement interpretation and broader export/recovery coverage remain open.

## API 37 subscription reminder delivery — 2026-10-26

- On the API 37 emulator, created only a zero-balance QA wallet and a synthetic active monthly subscription (`QA Reminder`, ₱100, due Oct 26, 2026). Set reminders to the due day and temporarily changed the emulator timezone from GMT to Pacific/Noumea (UTC+11) to observe the scheduled local 9:00 AM alarm.
- Android AlarmManager showed the app's real `RTC_WAKEUP` alarm for 9:00 AM. At 9:02 AM, Android's active NotificationManager record confirmed delivery by RalLedger with title `Subscription due today` and content `QA Reminder · ₱100.00`. The notification also carried the generic public lock-screen version, `Upcoming subscription reminder` / `Open RalLedger to view the details.` This verifies actual alarm dispatch and notification posting.
- In a second synthetic-only profile, enabled reminders with `1 day before`, added a QA monthly subscription due Nov 24, and confirmed the 9:00 AM local alarm. After force-stopping and relaunching RalLedger, the alarm remained scheduled and Settings still showed reminders enabled with `1 day before` selected.
- Cleanup: cleared both synthetic-only RalLedger profiles, restored automatic timezone selection and GMT, and returned `POST_NOTIFICATIONS` to denied, its initial test state. No RalLedger reminder alarm remained scheduled. Emulator display is 1080×2400 at 420dpi, text scale 1.0, and TalkBack disabled. No real financial data was used.
- The emulator was rebooted with reminders enabled and the QA subscription still stored. Android queued `BOOT_COMPLETED` while the emulator was locked; after unlock, the receiver ran and restored the Nov 23, 9:00 AM GMT alarm without opening RalLedger. The first boot callback could read app state before startup recovery was ready, so the receiver now waits for `StartupState.Ready` before reading subscriptions and preferences.
- Verification after the fix: 110 unit tests passed; lint reported 29 warnings and 0 errors; debug assembly succeeded; all 42 connected API 37 tests passed on a warmed emulator. One initial full connected run hit a transient Android System UI “isn't responding” dialog (38/42); the two affected test classes passed individually after recovery, and the complete suite then passed 42/42.
- Refreshed `releases/RalLedger-debug.apk`: 76,759,875 bytes; SHA-256 `5C5CAE98E1B5FE343C51FE1F9DEF4E4715FA4FDA9C7CC5998A58DF5518FD6060`. APK Signature Scheme v2 verifies. The APK remains debug-signed.
- Cleanup: removed the synthetic test profile and reminder alarm, restored GMT with automatic timezone enabled, and installed the refreshed APK with an empty profile and notification permission denied. Display is 1080×2400 at 420dpi, text scale 1.0, and TalkBack disabled. No real financial data was used.
- Remaining release gates include full TalkBack and larger-text review, the rest of the manual journeys, and a production-signed build.

## Phase II — API 37 selectable-text PDF picker import — 2026-10-26

- Started from an empty API 37 emulator profile. Created a zero-balance synthetic `QAWallet` and selected a generated, selectable-text PDF from Android's OpenDocument picker.
- The review flow detected the PDF statement, extracted one row (`2026-06-01 Freelance Payment 12.50`), and initially flagged the missing account. Mapped all rows to `QAWallet` and used the existing `Freelance & Consulting` income category. The preview then showed `Ready 1 · Saved duplicates 0 · Repeated rows 0 · Needs attention 0`, with the expected date, income amount, description, account and category.
- Confirmed the import. RalLedger reported `1 added, 0 duplicates skipped`; Accounts showed `QAWallet` at ₱12.50, and Activity showed `Freelance Payment`, `QAWallet | Freelance & Consulting`, and `+₱12.50` on Jun 1, 2026.
- Cleanup: cleared the synthetic-only app profile, removed only the two PDF files created for this check, and confirmed Home returned to ₱0.00 available money and ₱0.00 net worth. Emulator display remains 1080×2400 at 420dpi and 1.0x text; automatic time and timezone remain enabled. Screenshots and UI hierarchy are in ignored `.ralledger-local/qa/pdf-import-account-balance.png`, `pdf-import-activity-after.png`, `pdf-import-activity-after.xml`, and `empty-profile-after-pdf.png`.
- No real financial data was used. This verifies the selectable-text PDF import through the picker and confirmation flow; scanned-PDF OCR, diverse bank-specific statement layouts, and multi-amount PDF mapping remain unverified manually. Source code, tests and APK did not change, and automated tests were not rerun in this slice; the latest regression remains 110 unit tests, 42 connected API 37 tests, lint at 29 warnings / 0 errors, and successful debug APK assembly. The APK remains debug-signed.

## Phase II — Import result wording and regression rerun — 2026-10-26

- The smart statement-import flow used CSV-specific wording after a successful PDF import. Updated the shared success and failure messages to use format-neutral “Import complete” and “Import stopped” wording; import behavior is unchanged.
- Regression/build command completed successfully: 110 unit tests and 42 connected API 37 tests passed; lint reported 29 warnings and 0 errors; debug APK assembly succeeded. No test failures or skips were reported.
- Refreshed `releases/RalLedger-debug.apk` (76,759,875 bytes; SHA-256 `5CE2565214F04A9D69601C06771C489C6A749E8B4E466FB588B6FB45821EF811`). APK Signature Scheme v2 verifies. This artifact is debug-signed, not production-signed.
- Manual recheck of the updated message could not complete: after installing and clearing only the synthetic QA profile, API 37 repeatedly left RalLedger on its startup screen and logged `ANR in com.ralledger` / `failed to complete startup` during cold code verification. No import was attempted against the refreshed build. The prior selectable-text PDF import walkthrough did complete and verified its transaction and ₱12.50 account balance; scanned-PDF OCR, varied bank layouts, and multi-amount mapping remain unverified manually.
- The emulator app profile was cleared; no real financial data was used. Full screen-reader review, the remaining Phase II device journeys, and production signing remain open.

## Phase II — Post-fix PDF import confirmation — 2026-10-26

- Repeated the Android 17/API 37 selectable-text PDF picker walkthrough on the refreshed build after the generic import-message fix. The first picker attempt selected an unrelated pre-existing CSV and was canceled before import; the successful retry searched for and selected only the synthetic PDF.
- The PDF review extracted the synthetic row `2026-06-01 Freelance Payment 12.50`. After assigning the existing `QAWallet` and `Freelance & Consulting` category, the preview reported `Ready 1`, zero duplicates, zero repeated rows, and zero rows needing attention. The preview showed Income, ₱12.50, Jun 1, 2026, the description, account, and category.
- Confirming the row displayed the format-neutral message `Import complete: 1 added, 0 duplicates skipped.` The account balance became ₱12.50 and the Activity journal showed `Freelance Payment` as `+₱12.50`. After force-stop/relaunch, Home still showed ₱12.50 available money and net worth; this verifies database persistence across process restart.
- Cleanup cleared the synthetic-only app profile and removed only the synthetic PDF and XML files created for this recheck. Home returned to ₱0.00 available money and ₱0.00 net worth. Pre-existing Downloads CSV files were left untouched.
- Screenshot evidence is in ignored `.ralledger-local/qa/`: `pdf-preview-row-final.png`, `pdf-import-result.png`, `account-balance-pdf.png`, `activity-pdf.png`, `relaunched-after-import.png`, and `empty-after-cleanup.png`.
- This was a manual verification of the previously built APK/source state; no source code or APK changed and automated tests were not rerun. Latest automated verification remains 110 unit tests, 42 connected API 37 tests, lint with 29 warnings and 0 errors, and successful debug assembly. PDF OCR, varied bank layouts, multi-amount PDF mapping, full TalkBack review, and production signing remain open.

## Release signing configuration — 2026-10-26

- Added opt-in release signing to the Android Gradle configuration using either local Gradle properties or CI environment variables. The keystore and passwords remain outside the repository. Partial configuration and missing keystore paths fail early with an actionable message.
- Added `RELEASE_SIGNING.md` documenting the four settings and the unsigned-build behavior. The release build remains unminified until it receives separate runtime verification.
- Verification: unsigned `:app:assembleRelease` passed and produced a 67,721,801-byte APK. A partial signing configuration failed with the intended “needs all four” error; a complete configuration pointing to a missing keystore failed with the intended missing-file error.
- Generated an ignored 30-day local test key, built the signed release variant successfully, and verified APK Signature Scheme v2. Its certificate was `CN=RalLedger Local Test Key` (SHA-256 `b381cc3fb65c71798fa77f6b40ba00e1e2c341ca889d78d84888bd768cf808b9`); the 67,734,089-byte test-signed APK and test keystore were deleted afterward. This test key is not a production signing key.
- Installed that test-signed release on API 37 and confirmed it reached Home with zero balances. Reinstalled the tracked debug APK and again confirmed Home displayed ₱0.00; the emulator was left with the debug build and an empty profile.
- No owner-managed production keystore or credentials are present. A production-signed APK is still blocked on the owner supplying signing credentials. R8/minification remains disabled pending separate release-variant runtime validation.

## Phase II — Compact large-text Quick Add layout — 2026-10-26

- At API 37, checked the Quick Action hub and transaction form at 320 dp (840×1860 at 420 dpi) with 1.3× system text. The three primary Quick Action tiles now use a two-column layout at compact widths; the transaction type selector uses the same reflow rule so Expense, Income, and Transfer stay legible. Account, destination-account, and category selectors now show their labels separately from the selected value, keeping the empty-state prompts readable.
- On a disposable empty profile, created a zero-balance `QA wallet`. The transaction form showed its ₱0.00 balance above the amount field without overlap. The calculator evaluated `12 + 3` as `15.00`, and Use amount placed the result into the unsaved draft. The scrollable form reached Save. The draft was discarded and no transaction was posted.
- Cleared the QA profile after inspection, relaunched Home, and confirmed ₱0.00 available money and net worth. Emulator settings were restored to 1080×2400, 420 dpi, and 1.0× text. Screenshots are in ignored `.ralledger-local/qa/` under `phase2-quick-actions-320-text130-final.png`, `phase2-transaction-form-320-text130-fixed.png`, `phase2-calculator-result-large-text.png`, `phase2-calculator-applied-large-text.png`, `phase2-form-account-balance-large-text.png`, and `phase2-profile-empty-final.png`.
- Regression: 110 unit tests passed; 42 connected API 37 tests passed at normal display settings; lint reported 29 warnings and 0 errors; `:app:assembleDebug` succeeded. The first connected run at compact size and 1.3× text had 41/42 pass: `dashboardWidgetSwitchExposesItsLabelAndStateAsOneControl` did not find the Recent transactions switch in the visible semantics tree. The full suite passed after returning to the default display settings; large-text Settings accessibility still needs targeted review.
- Refreshed `releases/RalLedger-debug.apk` from the verified build: 76,699,389 bytes; SHA-256 `74173B469D5F37FAB6A759496FBB24FE95FE2AE804767C69F11C69055E668949`. APK Signature Scheme v2 verifies. It is signed with the Android debug certificate, not an owner-managed production key; the production-signed APK remains unavailable until the owner provides signing credentials.
- Published commit `56b3a98c6318dc57fee4259119f36acb191c5caf` and the refreshed APK to `main` in [Ralimations/RalLedger-finance-management](https://github.com/Ralimations/RalLedger-finance-management). GitHub accepted the APK and warned that its 73.15 MB size exceeds the recommended 50 MB threshold.

## Phase II — Dashboard Settings large-text accessibility reachability — 2026-10-26

- The large-text accessibility test previously checked for the Recent transactions switch while it was below the initial viewport of the scrollable Dashboard settings list. Updated the test to use accessibility scroll-forward actions until that labeled switch is reachable, then assert it is exposed as a checkable control. This is a test-only fix; app behavior is unchanged.
- Verification: focused Dashboard Settings instrumentation passed 2/2 tests at 320 dp and 1.3× text. The full API 37 connected run passed 42/42 tests across 25 suites; unit tests passed 110/110. No failures, errors, or skips were reported. `:app:lintDebug` and `:app:assembleDebug` completed successfully; lint has 29 warnings and 0 errors, and APK assembly was up to date.
- Restored the emulator to 1080×2400, 420 dpi, and 1.0× text. Reinstalled the tracked debug APK into an empty emulator profile and confirmed Home reports ₱0.00 Available Money and ₱0.00 Net Worth. No real financial data was used. The APK remains debug-signed; production signing and the remaining manual accessibility/release checks are still open.

## Phase II — Shared transaction details and editable date/time — 2026-10-26

- Added a shared date/time selector for Quick Add and transaction correction. The date/time controls are inside the collapsed `Add details (optional)` section with description and notes; transfers also expose an optional fee and fee category.
- Added timezone-safe local date/time conversion helpers. Quick Add labels recent account/category defaults as suggestions; manually chosen values are retained and are not presented as inferred suggestions.
- Added unit coverage for local date/time changes and default-source classification. Instrumentation verifies the optional section starts collapsed, expands, and opens/cancels both pickers without posting a record.
- Verification passed: 115 unit tests and all 43 connected API 37 tests, with 0 failures, errors, or skips; lint reported 29 warnings and 0 errors; `:app:assembleDebug` succeeded. The offline `emulator-5562` was skipped.
- Installed the refreshed debug APK on `emulator-5554`, cleared the newly installed QA profile, and confirmed the foreground Home screen shows ₱0.00 Available Money and ₱0.00 Net Worth. Screenshot: `.ralledger-local/qa/phase2-clean-launch.png`. Android's `uiautomator dump` timed out connecting to its accessibility bridge, so the launch was confirmed from the direct screenshot and Activity state instead.
- Refreshed `releases/RalLedger-debug.apk`: 76,630,462 bytes; SHA-256 `D3D553D0FCCAC4478DBB11D037F57BC0726BF12469A15C575D94ABACCC5E1C18`. APK Signature Scheme v2 verification passed; signer is Android Debug, not a production key.
- Published commit `10a622d6dbc53d2e673d5d82c7d4b27b1b982541` and the refreshed APK to `main` in [Ralimations/RalLedger-finance-management](https://github.com/Ralimations/RalLedger-finance-management/commit/10a622d6dbc53d2e673d5d82c7d4b27b1b982541). GitHub accepted the APK and warned that its 73.08 MB size exceeds the recommended 50 MB threshold.
- This completes only the shared date/time and progressive-detail slice of Milestone B. Tags, repeat scheduling, counterparty selection, draft restoration, and broader screen-by-screen Phase II acceptance remain to be completed or verified. No real financial data was used.

## Phase II — Quick Add transfer save journey — 2026-10-26

- Added API 37 instrumentation that drives the real Quick Add transfer sheet on an empty test profile. It saves a ₱100 transfer with a ₱10 fee, an expense fee category, description, notes, and the selected timestamp, then checks the persisted transaction links and the source/destination ledger balances (₱390 and ₱100).
- The instrumentation helper now targets the amount, fee, description, and notes controls in their actual accessibility traversal order and scrolls to the lower optional fields before editing. The initial failures were test-harness field targeting; no production behavior change was needed.
- Verification: all 44 connected API 37 instrumentation tests passed with 0 failures, errors, or skips; 115 unit tests passed; `:app:lintDebug` and `:app:assembleDebug` succeeded; lint reports 29 warnings and 0 errors. The second configured emulator (`emulator-5562`) was offline and skipped.
- This checkpoint changes only instrumentation and documentation, so the already published debug APK remains unchanged. The test used synthetic account/transaction values only; the Android Gradle test run removed the test app afterward.
- The transfer-posting path is now covered end to end. Posting a deliberately edited date/time, tags, repeat scheduling, counterparty selection, draft restoration, remaining Phase II journey review, and production signing remain open.

## Phase II — Quick Add labels and saved counterparties — 2026-10-26

- Quick Add income and expense now allow selecting multiple active transaction labels and entering a counterparty. The counterparty field offers matching saved counterparties as suggestions; the saved name is stored as the transaction's counterparty text. Transfers keep their existing fee/category/date/notes flow.
- Repository posting validates that selected labels still exist and are active, then writes label assignments in the same Room transaction as the journal entry. A missing or archived selected label fails the posting transaction rather than leaving a partially tagged record. No database schema migration was needed.
- Added repository instrumentation for successful active-label assignment and rollback when a selected label has been archived. Extended the Quick Add journey to post a tagged expense using a saved counterparty, then post a transfer with a fee, category, description and notes, and verify persisted data and ledger balances.
- Verification: 115 unit tests passed; all 44 connected API 37 instrumentation tests passed with 0 failures, errors, or skips; `:app:lintDebug` reported 29 warnings and 0 errors; `:app:assembleDebug` succeeded. The offline `emulator-5562` was skipped. Automated UI coverage exercises the selection controls and persistence; a separate manual TalkBack and compact-width selection walkthrough remains open.
- Refreshed `releases/RalLedger-debug.apk`: 77,591,872 bytes; SHA-256 `B3A9E9EC343E3C9D4C479E0D35C964B6515E8469A7F4B2FDACC6752D1CA87F24`. APK Signature Scheme v2 verification passed with one Android Debug signer. This is a debug build, not production-signed.
- This completes the Quick Add label/counterparty implementation slice, not Phase II as a whole. Repeat scheduling, draft restoration, remaining manual journeys/accessibility checks, and owner-managed production signing remain open. All test records were synthetic.

## Phase II — Quick Add draft recovery and idempotent save — 2026-10-26

- Quick Add now retains its in-progress transaction when Android recreates the Activity. The saved draft includes transaction type, amount, account selections, category and labels, counterparty, description, notes, date/time, transfer fee details, advanced-details visibility, and a stable submission ID.
- Income, expense, and transfer posting use that draft ID as the journal entry ID. Within the Room transaction, a retry checks the existing entry and its labels: an identical retry returns the committed result without changing balances or creating another journal row; reusing the ID with changed data is rejected with an actionable message. No schema migration was needed.
- Added API 37 instrumentation for restoring an edited draft after Activity recreation and for expense/transfer retry behavior, including unchanged balances/row counts and rejection of a mismatched retry. The draft is discarded at the end of the restoration test and the test confirms it did not post a transaction.
- Verification: 115 unit tests passed; all 45 connected API 37 instrumentation tests passed with 0 failures, errors, or skips; `:app:lintDebug` reported 29 warnings and 0 errors; `:app:assembleDebug` succeeded. The configured `emulator-5562` was offline and skipped.
- Refreshed `releases/RalLedger-debug.apk`: 76,222,984 bytes; SHA-256 `28F40BF377B5D67B610246C8483747DD94769ACB670AE3D239EE68BD6DA0ED7A`. APK Signature Scheme v2 verifies; signer is Android Debug, not a production key.
- Remaining release work includes repeat scheduling, broader screen-by-screen and TalkBack review, remaining manual journeys, and owner-managed production signing. This completes the draft-recovery/idempotency slice only; Phase II remains in progress. Test data was synthetic; no real financial data was used.
- Published source, documentation, and APK in commit `2cfa3c0fa6e597b02da82d12179ca966567aa0f0` to `main`: [GitHub commit](https://github.com/Ralimations/RalLedger-finance-management/commit/2cfa3c0fa6e597b02da82d12179ca966567aa0f0). GitHub accepted the APK and warned that its 72.69 MB size exceeds the recommended 50 MB threshold.

## Phase II — Quick Add transaction templates — 2026-10-26

- Quick Add now exposes the existing transaction-template manager. A selected template fills the current draft's type, amount, account, destination, category, fee, counterparty, description, and notes, and expands optional details for review. Selecting a template never posts; the user must still choose Save.
- Focused API 37 instrumentation passed 3/3: optional details and editable date/time, unsaved draft restoration after Activity recreation (including Notes), and template prefill followed by a saved expense. The template case also verifies the journal is unchanged before Save and checks the saved account/category/details after posting.
- Regression verification: 115 unit tests passed; `:app:lintDebug` reported 29 warnings and 0 errors; `:app:assembleDebug` succeeded. No real financial records were used.
- Refreshed `releases/RalLedger-debug.apk`: 76,481,620 bytes; SHA-256 `95593B7FFB2D322EE15074A1A8F1EF0BC27BAF5D56EC7E42BA0DAD5C815A3E12`. APK Signature Scheme v2 verifies with the Android Debug certificate; this is not a production-signed APK.
- Source commit: `f399583efb8dc45e88ccb4ce1008b2b14840ad77`. Phase II remains in progress: automatic repeat scheduling, complete screen/TalkBack journeys, remaining accessibility/device coverage, and owner-managed production signing are open.

## Phase II — Full regression checkpoint — 2026-10-26

- Ran `:app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug` from `TestApplication`. Build completed successfully: 115 unit tests and all 45 connected API 37 instrumentation tests passed; failures, errors, and skips were 0. The offline `emulator-5562` was not available; connected verification ran on `emulator-5554`.
- Lint completed with 29 warnings and 0 errors. Debug APK assembly succeeded; the checked-in artifact remains `releases/RalLedger-debug.apk` (76,481,620 bytes, SHA-256 `95593B7FFB2D322EE15074A1A8F1EF0BC27BAF5D56EC7E42BA0DAD5C815A3E12`). It verifies with APK Signature Scheme v2 and the Android Debug signer, not a production key.
- This was a verification-only iteration; no app behavior or financial records changed. Test data was synthetic. Phase II remains in progress, with repeat scheduling, broader manual/accessibility coverage, remaining user journeys, and owner-managed production signing still open. This checkpoint does not publish or push changes.

## Phase II — Repeat schedule calendar foundation — 2026-10-26

- Added deterministic daily, weekly, monthly, quarterly, and annual occurrence-date calculation anchored to the original local date and time. Monthly schedules retain their original day across short months (including Jan 31 → Feb 28 → Mar 31), annual schedules return to Feb 29 in leap years, and the original wall-clock time is preserved across daylight-saving changes.
- Added six unit cases for month-end drift, quarterly cadence, leap years, daily/weekly cadence, daylight-saving behavior, and invalid inputs. Full unit suite passed 121/121; `:app:lintDebug` reported 29 warnings and 0 errors; `:app:assembleDebug` succeeded.
- Refreshed `releases/RalLedger-debug.apk`: 76,481,620 bytes; SHA-256 `CBF715C37964BAAEA6C2AA8EAC1AFFAC3E21BB8907A9B989E80257E83EA21688`. APK Signature Scheme v2 verified with one Android Debug signer; it is not production-signed.
- This is only the calendar-rule foundation. The recurring schedule model, Room persistence/migration, backup/restore, Quick Add repeat controls, schedule management, due reminders, and confirmed occurrence posting are not implemented yet. No connected-device tests were rerun in this domain-only slice; the previous full connected suite passed 45/45 on API 37. No real financial data was used, and no push or publish occurred.

## Phase II — Repeat schedules through confirmed posting — 2026-10-26

- Income and expense saves can now create daily, weekly, monthly, quarterly, or annual schedules anchored to the transaction's original local date and time. Quick Add shows the cadence and makes clear that future records are not posted automatically. Planning lists schedules and next dates; each occurrence opens the regular transaction form with saved details prefilled, and posting requires explicit confirmation. Users can pause, resume, or cancel a schedule. Transfers are not repeatable.
- Added Room schema 23 with an additive 22→23 migration, stored schedule labels and an occurrence ledger. Occurrence posting and schedule advancement are atomic, tied to journal entry IDs, and protected from duplicate retries. Encrypted backups now include schedule definitions, labels, and occurrence history; restore validates their linked accounts, categories, dates, and journal records. Backup preview includes schedule and occurrence counts.
- Verification: `:app:testDebugUnitTest` passed 121/121; `:app:lintDebug` completed with 29 warnings and 0 errors; debug and Android-test APK assembly succeeded. A direct API 37 runner passed the focused occurrence/backup round-trip test; a schema-1 migration preservation check also passed in a focused direct run. The connected Gradle task's report contained 0 tests after emulator app startup stalled, so the full connected suite was not verified on this snapshot.
- Refreshed `releases/RalLedger-debug.apk`: 76,712,582 bytes; SHA-256 `02BA7F8E69C6A26C00ADBCA4C5A5CAE34EA0A412E73B15F4494B12690F7E6A0D`. APK Signature Scheme v2 verifies with the Android Debug signer; this is not production-signed.
- Phase II remains in progress. Automatic schedule posting and reminders, editing an existing schedule, broader connected/device journeys, TalkBack review, and owner-managed production signing remain open. Test records were synthetic; no real financial data was used. No push or merge occurred in this iteration.

## PesoMind brand and Android opener — 2026-10-26

- Applied the supplied PesoMind identity to the Android launcher, app header, About, app-lock/recovery copy, widgets, notifications, export filenames, and new backup metadata. Legacy `RalLedger` backup metadata remains readable; the encrypted header and financial record format are unchanged. Added the supplied logo assets and brand guidelines; no monochrome logo was invented.
- Implemented AndroidX SplashScreen compat 1.2.0 for supported Android versions. The splash uses the supplied mark and remains only while startup recovery runs, with no fixed delay. New installs get one native Welcome screen with the mark, tagline, description, and Get Started action; DataStore stores its completion.
- Existing installations detect the prior Room database or saved preferences before database initialization, mark onboarding complete, then continue through the existing app-lock gate and dashboard. The app ID/namespace remain `com.ralledger`, Room filename `ralledger_db`, and schema unchanged. No website or WebView was added.
- Verification: 124 unit tests passed (0 failures/errors/skips); Kotlin compilation, Android-test compilation, debug and Android-test APK assembly passed; `:app:lintDebug` completed with 0 errors and 37 warnings. The focused branding instrumentation test was built but could not complete because the emulator disconnected and remained offline. Before the earlier update install, `ralledger_db` was present at 606,208 bytes; both APK update installs reported success, and no uninstall or data clear was issued. A post-install database check and first-run visual flow remain unverified.
- Refreshed `releases/RalLedger-debug.apk`: 83,662,435 bytes; SHA-256 `8A7C27B068D1070C15A07D6647EA09C90A60DC3F2D30ED16AF5AA9621B2997E5`. APK Signature Scheme v2 verifies with the Android Debug signer; it is not production-signed. The build output is also at `TestApplication/app/build/outputs/apk/debug/app-debug.apk`.
- No real financial data was used. No GitHub push, merge, or deployment occurred in this iteration.

## Phase II — Opener correction and safe recurring schedule edits — 2026-10-26

- Reviewed the supplied correction brief. It clarifies that “opener” means a native Android launch and first-run experience, not a public website. The existing `856d177` implementation already provides AndroidX SplashScreen and one-time Compose Welcome; the application ID, Room identity, app-lock route, and stored financial data remain intact. No website, WebView, separate frontend, or deployment artifacts were found or added.
- Added an editor to Planning for active and paused recurring income/expense schedules. It updates future amount, category, payee/source, description, and notes; account, cadence, anchor and next dates, end date, labels, and already-posted journal entries remain unchanged. Repository updates validate positive amounts, active category type, schedule state, and the caller's `updatedAt` revision before writing atomically.
- Added focused Room instrumentation asserting that template edits preserve the original journal row and occurrence history, leave cadence/index/date fields intact, and reject stale edits. The new instrumentation source compiles, but could not be executed because ADB reports the only configured emulator (`emulator-5562`) as offline.
- Verification: 124 unit tests passed; `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` succeeded. Lint reports 37 warnings and 0 errors. Debug APK Signature Scheme v2 verification passed. Refreshed `releases/RalLedger-debug.apk`: 83,662,435 bytes; SHA-256 `D7BA0F44A40BDD75478724BB2AAB4B111D5397F84A649DE1789A78EB4BA9C546`; signed with the Android Debug certificate, not production-signed.
- No real financial data was used. This is a local implementation checkpoint only; no push, merge, or publish occurred. Automatic recurrence reminders/posting, connected-device review, wider accessibility/device journeys, and production signing remain open.

## Phase II — Release variant build verification — 2026-10-26

- Ran the available release checks. `:app:lintRelease` completed with 37 warnings and no errors; `:app:assembleRelease` succeeded. The APK is `TestApplication/app/build/outputs/apk/release/app-release-unsigned.apk` (69,114,584 bytes; SHA-256 `247D5440A09C41FD6DA262B423452393BDBA12DD03216A1203B6479A63271ACD`). Manifest inspection confirms package `com.ralledger`, version 2.0 / code 2, min SDK 26, and target SDK 37.
- The release APK is unsigned because no owner-managed production keystore is configured, and release minification remains disabled pending runtime verification. The project does not define a `testReleaseUnitTest` task; the 124-test Debug unit suite remains the configured unit suite. The release APK was not installed or runtime-tested because ADB shows the only device offline and no AVD is configured.
- This is verification-only. No APK was copied to the tracked debug release path, no financial data was used, and no push, merge, or publication occurred. Production signing, minification decision/runtime verification, and device acceptance remain release gates.

## Phase II — Offline network permission hardening — 2026-10-26

- The release manifest-merger report showed `INTERNET` and `ACCESS_NETWORK_STATE` were contributed by transitive `transport-backend-cct`, not by PesoMind source. The source has no network client. Google ML Kit documents `com.google.mlkit:text-recognition` as the bundled option, with its model statically included and available offline; retained this artifact for local statement OCR.
- Added manifest merge removal rules for both network permissions and instrumentation asserting neither permission is in the installed app's requested-permission list. `aapt dump permissions` confirms both permissions are absent from the assembled debug and release APKs. The instrumentation source compiles, but no emulator is online to execute it or confirm OCR at runtime.
- Verification: the configured 124-test Debug unit suite remains green; `:app:lintDebug`, `:app:lintRelease`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:assembleRelease` succeeded. Both lint reports show 37 warnings and 0 errors. Release remains unsigned and unminified.
- Refreshed `releases/RalLedger-debug.apk`: 83,662,435 bytes; SHA-256 `A6682843F89328FFE748AA37BEACF22D89DA1C658C775A1564E047EFB65E67CB`. APK Signature Scheme v2 verifies with the Android Debug signer; this is not a production-signed artifact. The unsigned release output is 69,114,532 bytes; SHA-256 `BA4A5A5CDF9BB578642912507FDAC095F66D769FABEC0A25890F26E52B3C2B9D`.
- No real financial data was used. No push, merge, or publication occurred. Owner-managed production signing, release runtime/minification verification, and connected-device checks remain open.

## PesoMind opener correction and launch polish — 2026-10-26

- The supplied correction is implemented as a native Android app opener. The existing `856d177` checkpoint already contains the PesoMind system splash, one-time Compose welcome, DataStore completion state, existing-install detection, and app-lock routing. A repository scan found no marketing website, separate frontend, or WebView artifacts; the supplied root logo files were left untouched.
- This review found that transient startup/recovery screens forced Light mode while the saved theme was loading, and the welcome/opening/app-lock layouts did not explicitly reserve safe system-bar areas. `MainActivity` now applies the saved or system theme throughout startup and recovery, updates system-bar icon appearance consistently, and adds safe-drawing insets to the launch, recovery, and lock screens. The application ID remains `com.ralledger`; database identity and financial data were not changed.
- Verification after the UI correction: all 124 Debug unit tests passed; `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` succeeded. Lint reports 37 warnings and 0 errors. The APK label is `PesoMind`, package `com.ralledger`, version 2.0, and APK Signature Scheme v2 verification passed with the Android Debug signer.
- Refreshed `releases/RalLedger-debug.apk`: 83,662,435 bytes; SHA-256 `FEC728DD46A5DF158D8B748548790054685FA1B9F3839A158783B13FFCD11C15`. This is a debug-signed APK, not a production-signed release.
- The API 37 `Medium_Phone` AVD remained offline after a non-destructive ADB reconnect and server restart. The app UI, fresh-install/returning-user flows, theme rendering on device, and app-lock journey could not be observed in this iteration. No app data was cleared or modified; no real financial data was used.

## PesoMind opener correction — clean-install and returning-user verification — 2026-10-26

- Re-read the correction brief: “opener” means the existing native Android splash and short first-run welcome, not a website. The Android project remains the only app; no website, WebView, separate frontend, or deployment work was added. The PesoMind splash/welcome implementation was already present, so this iteration concentrated on verification and test setup.
- On an isolated API 37 emulator, a cleared test install showed the branded welcome with the supplied mark, tagline, description, and Get Started action. Tapping Get Started reached the PesoMind dashboard. Force-stopping and reopening the app returned directly to the dashboard without replaying welcome. Only synthetic empty test state was used; no personal finance records were involved.
- Updated MainActivity instrumentation fixtures to explicitly mark onboarding complete for tests that exercise returning-user screens. Quick Add checks now account for scrollable optional fields and report field state when assertions fail. No production app source changed in this iteration.
- Verification: `:app:testDebugUnitTest` passed 124/124; `:app:lintDebug` completed with 37 warnings and 0 errors; `:app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded. The latest API 37 connected suite passed 48/49 tests. One Quick Add draft-restoration assertion remains failing: the description text was not present after Activity recreation (amount, counterparty, and notes restored). This is outside the opener and remains unresolved; do not treat the connected suite as fully green.
- The built debug APK is 83,662,435 bytes (SHA-256 `1E083662AB853E58DCA4794B10C2D60947E5A14DA176F03C61D3270E6AC4249A`). The tracked `releases/RalLedger-debug.apk` was not replaced because app production source did not change; its prior published debug-signed artifact remains recorded above.
- The first-run and subsequent-launch paths are verified on API 37. This iteration did not repeat the installed-upgrade/database-preservation check or a manual app-lock visual smoke. Production signing and broader device/accessibility acceptance remain open. No GitHub push, merge, or publication occurred.

## PesoMind opener correction — verification follow-up — 2026-10-26

- Re-read the supplied correction: the requested “opener” is the native Android SplashScreen and first-run welcome. The committed PesoMind opener in `1d9dc6746413bea042bbd705124e02b41a4d68db` already implements it. A repository scan found no marketing website, separate frontend, or WebView work; root logo files were left untouched.
- The API 37 clean-install and returning-user walkthrough from the prior checkpoint showed Welcome → Get Started → dashboard, then a force-stop/relaunch directly to dashboard. This iteration made no production app changes.
- Refreshed the instrumentation fixture so returning-user tests complete onboarding before launching MainActivity. Unit tests passed 124/124; lint reported 37 warnings and 0 errors; debug and Android-test APK assembly passed. A 49-test connected run had 48 passing and one failure caused by the Quick Add test selecting the wrong Notes field index after optional-field navigation. After correcting the test index, the affected draft test passed 1/1; the other 48 cases had passed in the full run. The full suite was not rerun after that test-only correction.
- Description draft restoration after Activity recreation remains unverified. A Compose semantics test compiled, but the API 37 emulator's Espresso bridge failed at runtime with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`; the temporary Compose-test dependency and test were removed. The existing instrumentation therefore does not claim coverage for that field.
- The generated debug APK at `TestApplication/app/build/outputs/apk/debug/app-debug.apk` is 77,941,568 bytes, SHA-256 `09054C8B959DAC4456EE60BCDF62A46728CC8662B5595E9DA66E24FADFAE53FC`. The tracked release APK was not replaced. No real financial data was used, and no push, merge, or publication occurred.
- The opener acceptance flow is verified; Phase II remains active. Description-draft recovery, broader manual/accessibility journeys, and owner-managed production signing remain open.

## PesoMind opener — Quick Add lifecycle verification — 2026-10-26

- Added Compose instrumentation for an unsaved Quick Add description: enter text, recreate MainActivity, and verify the draft remains. The API 37 emulator passed this check. This demonstrates that the earlier missing assertion was a test coverage gap, not a confirmed app defect.
- Kept the Compose UI test library aligned with the app's Compose BOM (1.7.6) and updated Espresso to 3.7.0; this removed the test-only runtime class mismatch seen with an incompatible test library.
- Full verification passed: 124/124 Debug unit tests; 50/50 connected API 37 instrumentation tests; `:app:lintDebug` with 37 warnings and 0 errors; `:app:assembleDebug` and `:app:assembleDebugAndroidTest`.
- The current generated debug APK is 77,941,568 bytes, SHA-256 `B8D0FA74598B22A3A8A70861D64906A24ED36624440A950E7E5F46FEC26D9E4E`. It is debug-signed and remains a local build artifact; the tracked release APK and GitHub publication were not changed.
- This iteration changed test setup and added a Compose test tag to the Quick Add description field; it did not change accounting behavior or the app opener flow. The full Phase II release remains in progress: production signing, broader device/accessibility acceptance, and installed-upgrade/database-preservation evidence remain open. No personal financial data was used, and no push, merge, or publication occurred.

## RalLedger-to-PesoMind installed-upgrade acceptance — 2026-10-26

- On a clean API 37 emulator, installed the historical pre-rebrand RalLedger debug APK from commit `ff8f4d0` (SHA-256 `02BA7F8E69C6A26C00ADBCA4C5A5CAE34EA0A412E73B15F4494B12690F7E6A0D`). It and the current PesoMind APK share application ID `com.ralledger`, version code 2, and the same Android Debug signing certificate.
- Seeded one synthetic physical-wallet account with a PHP 246.80 opening balance and its journal entry through the old app, then installed the current PesoMind APK in place without uninstalling or clearing data. Verification confirmed the account and opening-balance journal remained, the existing-install welcome policy resolved complete, and launching the updated app displayed the dashboard rather than Welcome.
- The old and current builds both use Room schema 23; this checks app rebranding/update data retention and does not exercise a schema migration. After verification, removed only the test app and instrumentation package from the previously clean emulator and deleted the temporary historical APK copy. No personal financial data was used.
- The upgrade test is an opt-in instrumentation fixture (`upgradePhase=seed` / `upgradePhase=verify`) and is excluded from the standard connected suite so it cannot seed records during general tests. The Quick Add template scroll assertion now reports visible UI state on failure.
- Final standard verification passed: 124/124 Debug unit tests; 50/50 connected API 37 tests; Debug lint with 37 warnings and 0 errors; Debug and Android-test APK assembly. Debug APK: 77,941,568 bytes, SHA-256 `B8D0FA74598B22A3A8A70861D64906A24ED36624440A950E7E5F46FEC26D9E4E`.
- Phase II remains active. Production signing and broader device/accessibility acceptance remain open. No push, merge, or publication occurred.

## Exact-centavo display and regression verification — 2026-10-26

- `Money.format` now formats `BigDecimal.valueOf(centavos, 2)` instead of converting stored centavos to `Double`; this preserves exact cents for amounts above the binary floating-point integer precision limit. Added JVM and API 37 instrumentation regressions for a value ending in `.93` at that boundary.
- Corrected the Quick Add saved-template test to scroll until its amount input is actually visible. The previous helper stopped at a pinned header and caused a false failure. The affected class passed 3/3 tests, then the full suite passed 125/125 Debug unit tests and 51/51 connected API 37 tests. After changing deprecated locale construction to `Locale.forLanguageTag`, the focused JVM and Android money-format tests passed again.
- Debug and Release lint completed with 37 warnings and 0 errors. Debug, Android-test, and Release APK assembly succeeded. Release remains unsigned and unminified because the owner-managed signing key is not configured.
- Refreshed `releases/RalLedger-debug.apk` from the verified Debug build: 77,941,568 bytes, SHA-256 `A4A5E3C767911A0005085854328EB512149DE524BA774C6064D713A2D0715B6B`. The unsigned Release artifact is 69,114,532 bytes, SHA-256 `506A2FD0C4BDB7E46ADF5A53BA359012F5B1FEC478C86491BCB3DBF6349F336D`.
- No real financial data was used. The supplied root logo files remain untouched. Phase II remains in progress; release signing, release runtime validation, broader accessibility/device acceptance, and remaining CRUD gaps remain open. No push, merge, or publication occurred.

## XLSX import archive-boundary hardening — 2026-10-26

- Found that XLSX format detection advanced through ZIP members with `ZipInputStream.nextEntry` without explicitly bounding the drain of prior members. A crafted compressed entry before `xl/workbook.xml` could expand before the parser's 40 MB cap was reached.
- Format detection now explicitly drains members while enforcing the parser's 128-entry and 40 MB expanded-data limits. Parsing reuses the detected format so the same XLSX is not decompressed again for a second detection pass. The parser's workbook and cell limits remain in place.
- Added a JVM regression test that constructs a sub-100 KB ZIP expanding beyond 40 MB before its workbook index; detection rejects it at the configured bound. Added an API 37 instrumentation assertion that routes a synthetic valid XLSX through format detection and the complete file reader.
- Verification: 126/126 Debug unit tests passed; the focused XLSX API 37 instrumentation case passed 1/1; Debug lint has 37 warnings and 0 errors; Debug and Android-test APK assembly succeeded. The latest complete connected suite remains the separately recorded 51/51 run. `releases/RalLedger-debug.apk` is refreshed at 78,027,213 bytes, SHA-256 `967CA479728CD8837B70E38F6F29D810E0A4DC5413A86CEC70609B14F53FC917`; APK Signature Scheme v2 verifies with the Android Debug signer. Release remains unsigned and unminified.
- No real financial data was used. The root logo files remain untouched. PesoMind's native opener remains as implemented and no website work was added. Phase II remains active; signing and broader accessibility/device acceptance remain open.

## OFX and QIF transaction scan limits — 2026-10-26

- The OFX reader previously converted every regex match to a list before applying its 50,000-transaction limit. The QIF reader likewise built every row before rejecting overflow. Both now enforce the existing limit as each row is scanned, before adding row 50,001.
- Added synthetic 50,001-row regressions for both formats. The full Debug unit suite passed 128/128; Debug lint reported 37 warnings and 0 errors; Debug and Android-test APK assembly succeeded. The connected suite was not rerun; the latest complete result remains 51/51, with the separate focused XLSX instrumentation check passing.
- Refreshed `releases/RalLedger-debug.apk`: 78,027,140 bytes, SHA-256 `6F7F6B757D808B7F93D7800E7C9B940BB4BBCBD0D70384B3DB52220266E748D3`; APK Signature Scheme v2 verifies with the Android Debug signer. Release remains unsigned and unminified.
- No real financial data was used. Phase II remains active; owner-managed signing and wider device/accessibility acceptance remain open.



## PesoMind correction audit and import resource budgets — 2026-10-26

- Re-read the supplied correction: PesoMind's opener is a native Android splash and one-time welcome. The existing Android implementation already covers this path; tracked-file and history searches found no marketing site, WebView, separate frontend, or deployment project. The user-supplied root logo files were left untouched.
- Reviewed startup behavior: AndroidX SplashScreen holds during actual startup recovery; the welcome completion flag is in DataStore; existing installs with prior database/settings skip onboarding; the existing app-lock gate protects the dashboard. The app ID remains com.ralledger and the Room database identity and records are unchanged. The clean-install Welcome → Get Started → dashboard and returning-user relaunch were manually verified in the earlier API 37 opener walkthrough recorded above; this iteration did not clear app data or repeat that manual flow. The app-lock instrumentation check passed in this iteration.
- CSV now rejects more than 500,000 scanned cells or a field over 100,000 characters. XLSX rejects more than 500,000 worksheet cells/shared strings and bounds cell text at 100,000 characters. The checks run during parsing; existing 50,000-row, 100-column, and 40 MB expanded-workbook limits remain. Added CSV cell/field and XLSX cell-overflow regressions.
- Verification on API 37: 130/130 Debug unit tests and 52/52 connected instrumentation tests passed with 0 failures and 0 skips. Debug lint reported 37 warnings and 0 errors. Debug and Android-test APKs assembled as part of the connected run.
- Refreshed releases/RalLedger-debug.apk: 78,027,840 bytes; SHA-256 1E498AA3811AA2B308B27411F9351465846994C894F4C548D07FF9CDF7E74B55. APK Signature Scheme v2 verifies with the Android Debug signer. This is not a production-signed release.
- No real financial data was used. Release signing and broader device/accessibility acceptance remain open.


## Recurring schedule edit dialog — API 37 verification — 2026-10-26

- Added Compose instrumentation for the actual recurring-schedule edit dialog. It uses an in-memory Room database with synthetic data, changes the future amount, description, and notes through the UI, then verifies the original posted transaction remains unchanged.
- The focused API 37 UI test passed 1/1. The complete connected suite passed 53/53 with 0 failures, errors, or skips. The previously verified source baseline remains 130/130 Debug unit tests; Debug lint is 37 warnings and 0 errors.
- Production app source did not change, so releases/RalLedger-debug.apk remains current at 78,027,840 bytes, SHA-256 1E498AA3811AA2B308B27411F9351465846994C894F4C548D07FF9CDF7E74B55, APK v2 signed with the Android Debug signer.
- Updated the CRUD matrix: schedule editing now has connected UI evidence. Automatic posting/reminders, broader lifecycle checks, full accessibility review, and owner-managed production signing remain open. No personal financial data was used.

## PesoMind opener correction — current verification — 2026-10-27

- Rechecked the correction brief against the tracked project: the PesoMind opener is implemented in the native Android app with AndroidX SplashScreen, a Compose welcome screen, DataStore completion state, PesoMind launcher branding, light/night splash colors, and the existing app-lock gate. The install ID remains `com.ralledger`; no website, WebView, or separate frontend is tracked. Root logo assets were not modified.
- The first-install Welcome → Get Started → dashboard and returning-user startup have prior clean API 37 emulator walkthrough evidence; this check did not clear or reinstall the existing QA app data. Current connected-run coverage passed `PesoMindBrandInstrumentedTest` 2/2 and `AppLockGateInstrumentedTest` 2/2.
- Freshly executed Debug unit tests: 130/130 passed. `:app:lintDebug` and `:app:assembleDebug` succeeded; lint reported 37 warnings and 0 errors.
- Replaced the brittle index-based Quick Add test with Compose semantics. The passing end-to-end test verifies template prefill, expense/category/tag/counterparty persistence, explicit transfer source and destination selection, fee/description/notes, idempotent retries, and both account balances. Quick Add test tags identify amount, fee, description, notes, and account selectors without changing financial behavior.
- Full verification on API 37: 130/130 Debug unit tests and 53/53 connected instrumentation tests passed with 0 failures, errors, or skips. Debug lint reported 37 warnings and 0 errors; Debug and Android-test APK assembly succeeded.
- Generated Debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 78,298,888 bytes, SHA-256 `824C43C3E0FD1E66676FE0BF06E2512A2212136424A3DB11AC686E1192EFBA1D`. APK Signature Scheme v2 verifies with the Android Debug signer; this is not a production-signed release.
- The connected Gradle run removed the app from the emulator; `com.ralledger` is not currently installed, so no synthetic test fixtures remain there. No real financial data was used. This iteration did not change the already-verified opener behavior or publish to GitHub. Production signing and broader accessibility/device acceptance remain open.

## PesoMind native opener — Release QA and night-theme verification — 2026-10-27

- The latest correction is implemented as an Android-only experience. No marketing website, WebView, separate frontend, or deployment artifacts were found or added. The application ID remains `com.ralledger`; Room identity, schema, and financial data paths are unchanged. Supplied root logo files remain untouched.
- Aligned the Android night-mode platform theme with PesoMind's calm blue, ocean blue, deep indigo, and splash background resources. No Kotlin, accounting, database, or preference migration code changed in this iteration.
- Rebuilt Release successfully with `:app:lintRelease :app:assembleRelease`; Release lint reports 40 warnings and 0 errors. The production Release APK remains unsigned and unminified because no owner-managed signing key is configured.
- On API 37, a clean, empty test profile on a separate Release QA copy showed the native PesoMind welcome, then Get Started opened the dashboard. Dark-mode Welcome and Dashboard were visually checked; returning to light mode and force-stop/relaunch opened the dashboard without Welcome. The final test profile contains no financial records. Captures are in ignored `TestApplication/.ralledger-local/qa/pesomind-release-*.png`.
- The unsigned Release APK is `TestApplication/app/build/outputs/apk/release/app-release-unsigned.apk`, 69,114,432 bytes, SHA-256 `042AFCAA9FFCEB826935AC7263D845433C34169D018B7E67037B08681B8FCB03`. A separate QA copy at `TestApplication/.ralledger-local/qa/app-release-qa-debug-signed.apk` is 69,213,611 bytes, SHA-256 `1BDEC2F3FF901164D5530B87024F7A60D0564B8CCC8109D4364CAAA144651321`, signed with the Android debug certificate (SHA-256 `fabee57ffeb8f0ab41f1f3cd9ab03386c84a65d653f800b80ade78320ce14816`; v2 and v3 verified). This QA signature is not a production signature and the QA copy was not published.
- Latest source test evidence remains 130/130 Debug unit tests and 53/53 connected API 37 instrumentation tests from commit `f152e61012fbc67c1f463b173299b1bb172cef4c`; this theme-resource-only change was checked with Release lint/build and Release runtime smoke, not by rerunning the full test suite. Existing app-lock test coverage passed in that connected suite; a device-credential journey on personal hardware remains unverified.
- No GitHub push, merge, or publication occurred. Production signing, full TalkBack/text-scaling review, remaining manual journeys, and broader device/theme coverage remain open.

## PesoMind compact Welcome accessibility and release verification — 2026-10-27

- Made the native first-run Welcome content vertically scrollable while retaining its centered layout when it fits. This keeps the logo, title, description, and Get Started action reachable on short screens or with enlarged text; no accounting, database, authentication, or app identity code changed.
- On API 37 with a compact 720×1280 emulator display and 1.3× system text, confirmed the Welcome hierarchy exposes a scrollable container, swiped to Get Started, and opened the dashboard. A subsequent force-stop/relaunch showed the dashboard without replaying Welcome. Restored the emulator to 1080×2400 and 1.0× text. Test profile contained no financial records.
- Full verification on this source: `:app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:lintRelease :app:assembleRelease` succeeded; 130/130 unit tests and 53/53 connected tests passed with 0 failures, errors, or skips. Debug and Release lint each reported 40 warnings and 0 errors. A transient API 37 input-focus ANR was logged during the unrelated Quick Add date/time test; the test passed in the full run and a focused rerun passed 1/1 without another ANR.
- Latest unsigned Release APK: `TestApplication/app/build/outputs/apk/release/app-release-unsigned.apk`, 69,114,432 bytes, SHA-256 `7C129DBC7F7C9D5CDA74CE7F4CB1B5AA5BCD49703161A7A6952FB32D1C9BD2DF`. Debug QA APK SHA-256: `D333C8B55DB93F2A378CFC67959792360771E2E7BEB2341FDADC0D27AE34E677`. Release remains unsigned because no owner-managed signing key is configured.
- Tracked-file and history checks found no website, WebView, separate frontend, or deployment artifacts. The supplied root logo files remain untouched. No push, merge, or publication occurred. Production signing, broader device/theme checks, and a full TalkBack review remain open.

## Category lifecycle walkthrough and PesoMind opener correction — 2026-10-27

- Rechecked the supplied correction: the requested opener is already native Android (AndroidX SplashScreen followed by the one-time Compose Welcome); startup recovery, persisted completion, returning-user routing, and the existing app-lock gate remain intact. No landing page, WebView, website, or separate frontend was created, and no opener source change was needed.
- On API 37 using synthetic QA data at a 320dp-wide emulator viewport and 1.3× system text, created a QA cash account with ₱1,000 and a ₱10 expense under a child category. Category history showed the expense. Merging the child into its same-type root preserved the journal amount and left the account at ₱990. Archiving hid the category with the default filter; enabling archived categories exposed it, and Restore returned it to the active list.
- The focused category lifecycle walkthrough passed. The emulator app profile was uninstalled afterward, and display resolution, density, and system text scale were restored. No personal financial data was used. The earlier recorded full suite (130/130 unit tests and 53/53 connected tests) was not rerun in this documentation-only iteration.
- Updated `FEATURE_CRUD_MATRIX.md` to replace the category-specific manual verification gap with this API 37 evidence. Other CRUD gaps remain tracked; this does not mark the full category or Phase II roadmap complete. No push, merge, or publication occurred.
## Exact-source verification checkpoint and connected emulator incident — 2026-10-27

- Forced execution of :app:testDebugUnitTest with two workers passed 130/130 tests (0 failures, errors, or skips); the task was not served from the prior test cache.
- :app:lintDebug, :app:lintRelease, :app:assembleDebug, and :app:assembleRelease succeeded on this checkout. Both lint reports contain 40 warnings and 0 errors.
- The current Debug APK is TestApplication/app/build/outputs/apk/debug/app-debug.apk (78,298,282 bytes; SHA-256 7CE92CF8985112F9AD86CB117F1C2D16E37516F55D659A3746A3F591325FAF80), signed with the Android Debug certificate. Refreshed releases/RalLedger-debug.apk from this output. The Release APK is TestApplication/app/build/outputs/apk/release/app-release-unsigned.apk (69,114,432 bytes; SHA-256 7C129DBC7F7C9D5CDA74CE7F4CB1B5AA5BCD49703161A7A6952FB32D1C9BD2DF); it remains unsigned and unminified because no owner-managed key is configured.
- The full API 37 connected run executed 53 tests and reported 47 passes, 6 failures, 0 errors, and 0 skips. Both QuickAddProgressiveDisclosureInstrumentedTest cases timed out locating the Quick actions control; AppLockGate and DashboardSettings accessibility checks also failed, including an Accounts accessible-name assertion and a visible Android system ANR dialog.
- Emulator logcat recorded ANRs in the launcher, System UI, phone, media provider, and Google services; system displayed Process system is not responding. After reboot, a focused retry produced no instrumentation result files as system-wide ANRs recurred, so it was stopped. The emulator was shut down. Treat these connected UI results as failed/unverified pending a stable-device rerun; the prior 53/53 connected pass is an earlier independent checkpoint.
- The PesoMind native opener and app source were unchanged. No website, WebView, or separate frontend was added. No real financial data, push, merge, or publication occurred. Production signing, connected UI rerun, complete TalkBack review, and remaining Phase II journeys remain open.
## Stable isolated API 37 connected verification — 2026-10-27

- Created a fresh API 37 Google Play QA AVD in ignored project-local test storage; the existing Medium_Phone AVD and its user data were not touched. No real financial data was used.
- The first cold-start attempt displayed an Android System UI unresponsive dialog and the focused Quick Add tests failed before reaching their action controls. After allowing first-boot startup to settle, the fresh app displayed the native Welcome screen; Get Started opened Dashboard, where the enabled Quick actions accessibility node was present.
- Re-ran QuickAddProgressiveDisclosureInstrumentedTest: 2/2 passed. Then ran the complete :app:connectedDebugAndroidTest suite on the same warmed device: 53/53 passed, 0 failures, errors, or skips. This includes AppLockGateInstrumentedTest (2/2), DashboardSettingsAccessibilityInstrumentedTest (2/2), both Quick Add progressive-disclosure tests, smart import reader (4/4), and backup preference tests (7/7).
- This stable connected result supersedes the earlier 47/53 cold-start run. No Android source changed in this verification slice; the forced unit run (130/130), Debug/Release lint (40 warnings, 0 errors each), and Debug/Release assembly results remain the latest exact-source evidence.
- releases/RalLedger-debug.apk remains the current Debug build at 78,298,282 bytes, SHA-256 7CE92CF8985112F9AD86CB117F1C2D16E37516F55D659A3746A3F591325FAF80. Release is still unsigned and unminified. Full TalkBack, broader screen/device acceptance, and owner-managed signing remain open; no push or publication occurred.

## Money amount calculator and balance UI regression coverage — 2026-10-27

- Audited money-entry forms and confirmed they share the calculator-enabled `MoneyAmountField`; Quick Add and transaction entry also show a compact, scrollable account-balance summary. Added API 37 Compose instrumentation for the shared calculator result flowing into the amount field (`12 + 3` → `15.00`) and for active account balances remaining visible while archived accounts are omitted.
- The focused component suite passed 2/2. A first full connected attempt passed 49/55 and showed an Android System UI unresponsive modal during existing Quick Add, app-lock, and dashboard checks. After choosing **Wait** and letting the isolated API 37 QA emulator recover, the full connected suite passed 55/55 with 0 failures, errors, or skips.
- Forced Debug unit tests passed 130/130. Debug and Release lint each completed with 40 warnings and 0 errors. Debug, Android-test, and Release assembly tasks succeeded or were already up to date; the Android-test APK was rebuilt for the new instrumentation. No production app source changed in this verification slice.
- The PesoMind native splash and one-time welcome remain implemented; this correction added no website, WebView, separate frontend, or deployment artifacts. The `com.ralledger` application ID, Room identity, schema, and stored financial data were not changed. The four untracked root logo assets remain untouched. No personal financial data or publication was used; production signing, full TalkBack review, and remaining Phase II journeys remain open.

## PesoMind opener correction — scope recheck — 2026-10-27

- Re-read the supplied correction and checked the tracked working tree plus Git path history for website, landing-page, WebView, and Vercel artifacts. None were found, so there was no website-specific work to remove. The four untracked root logo files remain untouched.
- Confirmed the existing native flow: AndroidX SplashScreen during startup recovery; a Compose welcome screen once per install with DataStore completion; returning-user bypass; and the existing app-lock gate before dashboard access. PesoMind launcher/app branding and light/night splash resources are present. Application ID `com.ralledger`, Room identity, schema, and financial data remain unchanged.
- This was a documentation-only recheck; no Android source or app behavior changed. The immediately preceding exact-source verification recorded 130/130 Debug unit tests, 55/55 API 37 connected tests, and Debug/Release lint with 40 warnings and 0 errors each. Those suites were not rerun for this recheck. Production signing, full TalkBack review, broader device acceptance, and remaining Phase II journeys remain open. No push or publication occurred.

## Posted transaction type correction — API 37 verification — 2026-10-27

- Expanded the existing audit-safe correction workflow: ordinary income, expense, and transfer entries can now be corrected into any of those three types. The repository validates the target type's category/account/fee rules, then atomically posts a reversal and balanced replacement; the original journal remains unchanged. Linked and specialized postings remain under their dedicated workflows.
- The correction dialog now shows the Income/Expense/Transfer selector and explains the reversal/history effect when the type changes. Added API 37 repository coverage for income→expense, expense→transfer with fee, and transfer→income, plus a Compose test that selects a new type and verifies the saved correction fields.
- Forced Debug unit tests passed 130/130. The focused repository suite passed 5/5 and the focused Compose test passed 1/1. The full warmed API 37 connected suite passed 59/59 with 0 failures, errors, or skips. Debug and Release lint each reported 40 warnings and 0 errors; Debug and Release APK assembly succeeded. An initial full run's only failure was a test-host process mismatch; switching the Compose test to MainActivity resolved it, and the complete suite passed afterward.
- Current Debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`, 77,941,468 bytes, SHA-256 `D27FC4EFE4E6628FF0EFFE868F2CD6A6AFFFE6CAD2AA1961D7233901D2D04858`, signed with the Android Debug certificate. Release APK: `TestApplication/app/build/outputs/apk/release/app-release-unsigned.apk`, 69,114,432 bytes, SHA-256 `81C3D7341984B4C9FA7133CC1CE4E9B75B218973F63AA05DC135D32E4B4A6BCE`; it remains unsigned and unminified. No personal financial data was used. Refunds, specialized postings, owner-managed signing, full TalkBack review, broader device acceptance, and other Phase II gaps remain open.

## Refund entry, reporting, import, and duplicate support — API 37 verification — 2026-10-27

- Added Refund as a journal transaction that debits the selected asset account and credits an expense category. Refunds increase the receiving account and reduce expense, budget, and category-spending totals; they are never counted as income. Reversing a refund restores its account/category effect through the existing reversal journal. No Room schema migration was needed.
- Added the Refund action under Quick Action Hub → More and a Refund mode in transaction entry. The form requires an asset account and expense category, keeps the shared amount calculator/account balance display, and saves through the repository. Refunds can be duplicated into a new unsaved form, reversed with history preserved, filtered as expenses, and imported from CSV type aliases `Refund`, `Refund received`, `Return`, and `Purchase Return` against an existing expense category.
- Added regression coverage for balanced refund journal lines, repository idempotency/category validation/reversal, the duplicate form's Refund callback, CSV mapping/dedupe, expense filtering, budgets, dashboard totals, and Insights category/date reporting.
- Verification: `:app:testDebugUnitTest` passed 134/134; `:app:connectedDebugAndroidTest` passed 62/62 on API 37 with 0 failures, errors, or skips, including the Quick Action Hub More → Refund route. The second configured emulator was offline and skipped. Debug and Release lint each completed with 40 warnings and 0 errors; Debug and Release APK assembly succeeded. Both test source sets compile.
- Refreshed `releases/RalLedger-debug.apk`: 78,379,463 bytes, SHA-256 `64A08E864891D2885C866998689452CD1394C432B69E7FDE04E887A20D9838C9`; APK v2 signature verifies with the Android Debug signer. Release APK is 69,114,432 bytes, SHA-256 `426E43C879D8494B8DBD8864E66A0056DA6846CFFB62BBC4675109832D04F8FA`, unsigned and unminified. No real financial data was used. Owner-managed production signing, full TalkBack review, broader device acceptance, and remaining Phase II journeys remain open.

## PesoMind native opener — API 37 full recheck — 2026-10-27

- Re-read the correction brief and audited the current tracked project. The native Android opener already meets the requested scope; no website, WebView, separate frontend, or hosting/deployment artifacts were present, so no cleanup or app-source change was needed. Four supplied root logo files remain untouched.
- On the API 37 QA emulator, confirmed `com.ralledger` was not installed before the walkthrough and the device had no third-party packages. Installed the current Debug APK: AndroidX splash stayed only through startup recovery, then the first-run Compose Welcome showed the PesoMind mark, title, tagline, description, and Get Started action. Tapping Get Started opened the dashboard; force-stop/relaunch returned directly to the dashboard without repeating Welcome.
- Cleared only this newly installed, empty QA app profile and verified the Welcome screen survived a forced landscape rotation and restoration of the emulator's original orientation settings. The application ID remains `com.ralledger`; Room identity/schema and financial records were not changed. The existing app-lock gate remains ordered after Welcome and before financial screens, with `FLAG_SECURE` enabled while locked.
- Fresh verification: forced Debug unit tests passed 134/134; API 37 connected instrumentation passed 62/62 with 0 failures, errors, or skips, including PesoMind branding and app-lock checks. Debug and Release lint each reported 40 warnings and 0 errors. Debug and Release APK assembly succeeded; Release remains unsigned and unminified.
- No real financial data was used. No push, merge, or publication occurred. Production signing, full TalkBack review, broader device/theme acceptance, and the remaining Phase II journeys remain open.

## API 37 RalLedger-to-PesoMind installed-upgrade recheck — 2026-10-27

- Ran the opt-in installed-upgrade instrumentation fixture directly in two phases, outside the standard connected suite. Installed the historical pre-rebrand RalLedger APK from commit ff8f4d0 and confirmed its label, package, version code, and debug signer match the current PesoMind build. Seeded a synthetic physical-wallet account with a PHP 246.80 opening balance and journal entry, then installed the current PesoMind Debug APK over it in place.
- Both phases passed 1/1. The verify phase confirmed the account, opening journal, completed-welcome state, and Dashboard route persisted; Welcome was not shown. Both builds use version code 2 and Room schema 23, so this verifies the actual rebrand update and data retention, not a schema migration or version-code increment.
- The QA profile was empty before seeding. After verification, I cleared only the synthetic app profile, removed the instrumentation package, and deleted the temporary historical APK copy.
- No application source changed. No real financial data, push, merge, or publication was involved. Production signing, full TalkBack, wider device acceptance, and remaining Phase II journeys remain open.
