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
- Insights now total only income and expense activity for the selected month, omit reversed entries from the original period, and show category shares and normalized subscription commitments.

## Verification

- Environment: JDK 25.0.4.1 Temurin was downloaded from the official Adoptium API, SHA-256 checked against its official metadata, and placed in ignored `.ralledger-local/jdk25` so no project Java/Gradle versions needed changing. The installed SDK already includes API 35/37, build-tools 36.0.0, platform-tools, emulator, and an API 37 x86_64 system image.
- Passed: `:app:assembleDebug`
- Passed after the visual redesign: fresh `:app:assembleDebug --rerun-tasks` (all 38 tasks executed; no test tasks run in this UI pass).
- Passed: `:app:testDebugUnitTest` — 14 tests, 0 failures, 0 errors.
- Passed: `:app:lintDebug` — task succeeds; lint reports 20 guidance findings, mostly available SDK/dependency updates and target API guidance.
- Passed: `:app:compileDebugAndroidTestKotlin` — the new database/backup instrumentation test source compiles.
- Not run: `connectedDebugAndroidTest`. `adb devices -l` returned no devices, and `emulator -list-avds` returned no configured AVD. Do not treat database/backup instrumentation as runtime verified.
- Debug APK: `TestApplication/app/build/outputs/apk/debug/app-debug.apk`.
- Gradle verification used the locally cached Gradle 9.6.0 distribution directly. The wrapper attempted to reach `services.gradle.org` and network access was blocked in the non-escalated run; the project wrapper and Gradle configuration were not changed.

## Module readiness

| Module | Status | Limits |
|---|---|---|
| Money input and accounting engine | Implemented and unit-tested | Android screens still need hands-on validation |
| Accounts | Basic create, opening balance, archive, and ledger balances implemented | Edit, custom currency/icon/color, account history, and account-detail reconciliation are incomplete |
| Transactions | Income, expense, transfers/fees, search, and reversals implemented | Edit, duplicate, date/account/category filters, attachments/tags, refunds, and loan UI are incomplete |
| Receivables | Recognition, partial collection, due-date status, history records, and reversal logic implemented | Cancellation, write-off, editable due dates, and full payment-history UI are incomplete; instrumentation has not run |
| Reconciliation | Atomic adjustments for asset/liability accounts implemented and unit-tested | Screen accessibility is limited; unmatched-transaction workflow is incomplete |
| Subscriptions | CRUD foundation and monthly normalization implemented | Periods remain weekly/monthly/quarterly/annual; notifications, charge confirmation, and richer lifecycle controls are incomplete |
| Backup and restore | Encrypted SAF flow, validation, atomic replacement, and test source implemented | Runtime export/restore equivalence remains unverified until Android instrumentation runs. Current schema has no payables, budgets, savings goals, preferences, or time deposits to export |
| Dashboard | Totals and reversal-aware month summary implemented and unit-tested | User-specific account flags cannot be inspected without a device database |
| Appearance and insights UI | Saved theme mode/palette options; monthly interactive spending chart and clearer money summaries | Must be checked on device for motion, contrast, and small-screen layout; native home-screen widgets are still incomplete |
| Budgets, payables/debt, savings goals, expected income, time deposits, CSV/XLSX import, reports export, app lock, widgets, advanced charts | Incomplete | No end-to-end implementation in this checkpoint |

## Known audit findings and disposition

- The prior dashboard discrepancy is reproduced as a regression test under the stated account assumptions: liquid cash is ₱2,111; total assets and net worth are ₱17,111 when liabilities are zero and receivables are ₱15,000. The actual historical database flags were not available, so its specific cause is not claimed as proven.
- No existing user database was reset or migrated during verification. Schema 1→2 migration is installed in code but has not been exercised against a device database.
- The Android UI describes backup coverage accurately for the records that exist today. This is not a complete RalLedger v2 backup of the broader requested future modules.

## Android Studio validation checklist

- [ ] Open the project with JDK 25 and perform a clean debug build.
- [ ] Install and launch the debug APK on an emulator/device; confirm current user data remains intact after migration.
- [ ] Create an asset and liability account with opening balances; check balances and income reports.
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

1. Execute the instrumentation suite on an Android Studio emulator and verify schema migration with a v1 database.
2. Add idempotency tokens for user-submitted financial actions and expand repository-level failure/concurrency tests.
3. Complete account/category editing, budget CRUD, payables/debts, savings goals, expected income, and time deposits.
4. Implement transaction filters/editing, CSV import/export, richer reports, and complete statistics.
5. Continue the remaining screen polish and implement real home-screen widgets after the relevant data actions and widget scope are defined.
6. Re-run build, unit tests, lint, and instrumentation after each milestone; update this file before continuing.
