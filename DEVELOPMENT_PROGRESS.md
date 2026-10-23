# RalLedger v2.0 Development Progress

Updated: 2026-10-23
Branch: `feature/ralledger-v2-live-ready`
Baseline: `3cdb31c1d18fe1dace524ad6431c97686ec21faf`

## Delivered in this checkpoint

- Verified the original clean `master` baseline and created the requested feature branch. No remote is configured; nothing was pushed or merged.
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
| Receivables | Recognition, partial collection, editable client/description/due date, collection and write-off history, event reversals, cancellation, partial/full write-off, archive/restore, expense reporting and backup | Schema-8 connected lifecycle/migration/backup tests pass; manual screen workflows remain to be checked; no receivable-level permanent delete because recognition is posted |
| Expected income | Forecast records with category/source, counterparty, amount and date; convert once to a receivable with recognition journal; backup link validation | Connected migration, lifecycle, category merge and backup tests pass; UI smoke covered create/cancel/delete and editor rendering; conversion uses the existing receivable recognition policy |
| Bills and personal loans | Bill recognition to expense/payable; loan proceeds to cash/liability without income classification; partial principal and interest payments; payment reversal, cancellation by journal reversal, metadata edit, closed-record archive/restore, due-date state, search and encrypted backup | Connected debt lifecycle, reversals, category merge, schema migration and backup restore pass. Manual route and Add bill/Add loan form smoke passes; payment form interaction/restart still needs QA; posted amount/category cannot be edited or deleted |
| Reconciliation | Atomic adjustments for asset/liability accounts implemented and unit-tested | Screen accessibility is limited; unmatched-transaction workflow is incomplete |
| Budgets | Monthly expense-category budgets; create/edit/archive/restore/confirmed delete; live spend, remaining amount and overspend indicators; safe category references and merge handling; encrypted backup support | Connected budget lifecycle and merge tests pass. No carry-forward, fund allocation, or multi-period budget rules |
| Subscriptions | Daily/weekly/monthly/quarterly/annual schedule create/edit, pause/resume/cancel/reactivate, archive/restore, guarded delete, normalized commitments, categorized ledger payment, history and ordered reversal | Android build, unit tests and instrumentation-source compilation pass; connected schema-12 migration/payment/backup tests remain unrun because the emulator is offline; due reminders remain incomplete |
| Savings goals | Create/edit target, date and notes; searchable list; reserve/release existing liquid cash with dated history; archive/restore; delete only before use | Connected lifecycle and encrypted backup/restore passed before the final search-field edit; final source build/unit/lint/test compilation pass; screen interaction and restart persistence remain |
| Interest-bearing savings and time deposits | Schema-11 profile terms, interest/tax estimate, compounding, maturity, actual gross/tax/net credits, history and journal reversal; archive/restore and delete-unused | Android build and instrumentation-source compilation pass; calculator tests pass; runtime migration, repository/backup instrumentation and UI/restart smoke remain unverified because the emulator is offline |
| Backup and restore | Encrypted SAF flow, validation, atomic replacement, account descriptions, category hierarchy/archive state, budgets, expected income, savings goals/allocations, interest profiles/credits, bills/loans/payment audit trail, subscriptions/payment history, receivables and write-off history | Current Android build and instrumentation-source compilation pass; prior connected round-trip passed through schema 10. Schema-11/12 runtime round-trip remains unverified because the emulator is offline; older backups omit the new arrays |
| Dashboard | Available Money is primary; Net Worth is separate; debt liabilities, payable totals, loan proceeds and reversal-aware month cash flow flow from the ledger | User-specific account flags cannot be inspected without a device database |
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
- [ ] Manage income/expense categories: create, edit, reorganize, archive/restore, merge, view history and delete unused; confirm used history remains intact.
- [ ] Record an income, expense, transfer with fee, and reversal; inspect ledger and month totals.
- [ ] Create a receivable, edit client/details/due date, collect partially, attempt overcollection, reverse a collection, partially/full write it off, reverse a write-off, cancel, archive/restore, and verify journal and payment/write-off history.
- [x] Run the connected suite on an online emulator; populated schema-1/schema-2 migration chains through schema 10, debt/receivable/expected-income/savings-goal lifecycle, and encrypted backup round-trip passed before the final savings-goal search-field edit. Connected rerun after that edit was blocked because no emulator was online.
- [x] Verify repository reconciliation corrections for asset and liability accounts with positive and negative differences while preserving original opening journal entries.
- [x] Smoke-check account-menu reconciliation on emulator; verified adjustment text, reason entry, saved balance and success feedback.
- [ ] Add subscriptions with daily, weekly, monthly, quarterly, and annual billing; edit a schedule, pause/resume, cancel/reactivate, archive/restore, and verify normalized dashboard total.
- [x] Run subscription lifecycle and archived-subscription backup tests as part of the schema-8 connected suite. The populated schema-1→8 chain also traverses the 5→6 migration.
- [ ] Export an encrypted backup, restore it into an empty test install, and compare accounts, journal, receivables, payment history, and dashboard balances.
- [ ] Restart the app and verify records persist; inspect UI sizing, insets, dialogs, and navigation on multiple screen sizes.
- [ ] Switch System/Light/Dark and Ocean/Indigo/Sage in Settings; confirm selection persists after relaunch and chart colors remain readable.
- [ ] Validate day/week/month/year/custom/all-time Insights, custom date boundaries, reversals, category taps, source totals, and selected-bucket values against the journal.
- [x] Run `connectedDebugAndroidTest`; 13 tests passed, including backup and concurrent collection coverage.
- [x] Smoke-check expected-income tab and editor; create, cancel, and delete a synthetic forecast and verify the list returns to empty.
- [x] Verify Bills & loans, Add bill/Add loan forms, and Subscriptions tab routing after the final mapping correction.
- [ ] Complete hands-on Bills & loans payment entry, reversal, restart persistence, and small-screen checks.
- [ ] Smoke-check Savings goals create/edit, reserve/release, archive/restore, and persistence after restart in Android Studio.
- [x] Resolve the Foojay plugin using the approved Gradle download; debug build, 40 unit tests, lint, and Android-test source compilation pass.
- [x] Run schema-11/12 migration, interest and subscription-payment lifecycle/backup tests on an online emulator; completed in the schema-13 connected suite on `emulator-5554`.
- [ ] Smoke-check interest-bearing savings and time-deposit profile editing, estimates, actual credit, tax classification, reversal, archive/restore, account archive, backup/restore and relaunch persistence.
- [ ] Finish hands-on account/category/receivable/subscription workflows, settings persistence, charts, smaller-screen sizing, and SAF export/restore UI checks.

## Next development steps

1. Finish hands-on account/category/receivable/subscription CRUD, settings persistence, chart interaction, smaller-screen layout, and SAF export/restore checks.
2. Finish hands-on Bills & loans payment, reversal, restart persistence and small-screen checks.
3. Verify the schema-11 interest/time-deposit slice on an online emulator, including runtime migration, account/profile lifecycle, tax accounting, encrypted backup and device UI.
4. Verify linked subscription payment posting/history and multi-period Insights on an online emulator; then add subscription trend views, due notifications, CSV import and spreadsheet export.
5. Add remaining customizable preferences, optional PIN/biometric lock, export privacy controls, saved filters/reports and home-screen widgets; verify each persisted workflow and backup state.

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
- Manual flow verified on `emulator-5554` after the saved-filter implementation: saved and applied a temporary expense filter, changed current criteria and updated the saved filter, renamed it, then confirmed deletion. The manager initially clipped the final action; its controls now use two rows and all four actions fit. The temporary filter was removed, the development emulator had no transaction records, and no journal data was changed.
