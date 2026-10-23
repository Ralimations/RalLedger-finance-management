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
- Added encrypted SAF backup save/open flows using AES-256-GCM with a random nonce and PBKDF2-HMAC-SHA256 password derivation. Backups cover the current Room schema: accounts, categories, journal entries/lines, receivables/payments, subscriptions, and reconciliations. Restore previews counts and integrity errors, then replaces current rows atomically with a post-restore equivalence check.
- Disabled Android automatic data backup and excluded app data in both legacy and Android 12+ extraction rules; the portable in-app backup remains the supported recovery path.
- Corrected dashboard asset aggregation and moved it into a unit-testable calculator. Reversal postings are included as contra income/expense on the reversal date. Subscription commitments are normalized to monthly amounts.
- Added synthetic instrumentation coverage for concurrent receivable collection, payment reversal, and encrypted backup/restore equivalence. The test source compiles; it has not run on a device.
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
- Passed: debug APK launch smoke check on `emulator-5554`; Overview showed Available Money first with Net Worth separate, the transaction shortcut clear of the monthly summary, the Accounts empty state, and Categories opened from Settings. The category list and Add category dialog were visually checked; full account/category form submission and lifecycle actions remain outstanding. One offline emulator was skipped.
- Passed: transaction UI smoke on `emulator-5554` using only synthetic data. The journal filter panel rendered; Duplicate opened with account/category/amount/description/date prefilled; Correct opened with editable fields; saving a correction displayed the reversal, a Reversed marker on the original, and the replacement entry. The synthetic correction was posted only to the emulator test install.
- Debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`.
- Latest debug APK size: 21,920,301 bytes.
- Gradle verification used the cached Gradle 9.6.0 distribution copied into ignored `.ralledger-local` plus the local JDK 25. Repository access was needed to resolve Foojay and Android plugins. Project settings/wrapper versions were restored unchanged after diagnosis.

## Module readiness

| Module | Status | Limits |
|---|---|---|
| Money input and accounting engine | Implemented and unit-tested | Android screens still need hands-on validation |
| Accounts | Create, metadata edit, icon/color, dashboard inclusion, search, history, archive/restore, guarded unused delete and backup; lifecycle/migration tests pass | Currency/type/opening balance intentionally immutable after creation; full screen interactions still need manual QA |
| Transactions | Income, expense, transfers/fees, search, type/account/category/date filters, duplicate, reversal, and atomic correction by reversal plus replacement; connected correction tests pass | No draft lifecycle, transaction type conversion, attachments/tags, refunds, or loan UI; linked receivable postings cannot use generic correction. Full manual account/category CRUD UI validation remains outstanding |
| Categories and income sources | Create, rename, recolor, re-icon, one-level subcategory, search, archive/restore, history, merge and unused delete; migration/backup tests pass | Income sources are represented by income categories; no separate source analytics. Full screen interactions still need manual QA |
| Receivables | Recognition, partial collection, due-date status, history records, reversal logic and emulator instrumentation | Cancellation, write-off, editable due dates, and full payment-history UI are incomplete |
| Reconciliation | Atomic adjustments for asset/liability accounts implemented and unit-tested | Screen accessibility is limited; unmatched-transaction workflow is incomplete |
| Subscriptions | CRUD foundation and monthly normalization implemented | Periods remain weekly/monthly/quarterly/annual; notifications, charge confirmation, and richer lifecycle controls are incomplete |
| Backup and restore | Encrypted SAF flow, validation, atomic replacement, account descriptions, category hierarchy/archive state; emulator round-trip passes | No payables, budgets, savings goals, preferences, or time deposits exist yet to export |
| Dashboard | Available Money is primary; Net Worth is separate; totals and reversal-aware month summary are implemented and unit-tested | User-specific account flags cannot be inspected without a device database |
| Appearance and insights UI | Saved theme mode/palette options; monthly interactive spending chart and clearer money summaries | Must be checked on device for motion, contrast, and small-screen layout; native home-screen widgets are still incomplete |
| Budgets, payables/debt, savings goals, expected income, time deposits, CSV/XLSX import, reports export, app lock, widgets, advanced charts | Incomplete | No end-to-end implementation in this checkpoint |

## Known audit findings and disposition

- The prior dashboard discrepancy is reproduced as a regression test under the stated account assumptions: liquid cash is ₱2,111; total assets and net worth are ₱17,111 when liabilities are zero and receivables are ₱15,000. The actual historical database flags were not available, so its specific cause is not claimed as proven.
- No existing user database was reset or migrated during verification. Schema 1→2 migration is installed in code but has not been exercised against a device database.
- The Android UI describes backup coverage accurately for the records that exist today. This is not a complete RalLedger v2 backup of the broader requested future modules.

## Android Studio validation checklist

- [ ] Open the project with JDK 25 and perform a clean debug build.
- [ ] Install and launch the debug APK on an emulator/device; confirm current user data remains intact after migration.
- [ ] Create an asset and liability account with opening balances; edit metadata and verify type/opening balances and journal history remain unchanged.
- [ ] Search accounts, inspect transaction history, archive/restore an account, delete an unused account, and confirm a used account cannot be deleted.
- [ ] Manage income/expense categories: create, edit, reorganize, archive/restore, merge, view history and delete unused; confirm used history remains intact.
- [ ] Record an income, expense, transfer with fee, and reversal; inspect ledger and month totals.
- [ ] Create a receivable, collect partially, attempt overcollection, reverse a collection, and inspect outstanding/payment history.
- [ ] Reconcile an asset and a liability with both positive and negative differences.
- [ ] Add subscriptions with weekly, monthly, quarterly, and annual billing; verify normalized dashboard total.
- [ ] Export an encrypted backup, restore it into an empty test install, and compare accounts, journal, receivables, payment history, and dashboard balances.
- [ ] Restart the app and verify records persist; inspect UI sizing, insets, dialogs, and navigation on multiple screen sizes.
- [ ] Switch System/Light/Dark and Ocean/Indigo/Sage in Settings; confirm selection persists after relaunch and chart colors remain readable.
- [ ] Change insight months and tap chart segments/category rows; verify monthly totals match the transaction journal.
- [ ] Run `connectedDebugAndroidTest` and confirm the backup/concurrency test passes on-device.

## Next development steps

1. Continue hands-on account/category CRUD checks, including archive/restore and delete guards.
2. Implement the next financial modules with full CRUD and accounting coverage: budgets, payables/debts, savings goals, expected income, and time deposits.
3. Add CSV import/export and expanded statistics/reports, extending encrypted backup and restore coverage with each persisted module.
4. Finish subscription lifecycle controls, optional app lock, export privacy controls, and home-screen widgets after their underlying data/actions exist.
5. Manually validate transaction quick-add, filter/date controls, correction and reversal states, account/category forms, and smaller-screen layouts; rerun build/tests/lint after each milestone.
