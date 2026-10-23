# RalLedger CRUD and lifecycle matrix

Updated: 2026-10-23
Branch: `feature/ralledger-v2-live-ready`

This matrix distinguishes a working lifecycle from a create-only screen or repository primitive. “Partial” means one or more requested operations remain unavailable in the app UI or lack verified persistence. A button or repository method alone is not counted as complete.

| Record or feature | Create | View / search | Edit | Archive / pause / restore | Delete / void / close | History and integrity | Status |
|---|---|---|---|---|---|---|---|
| Financial accounts | Yes | Yes; list search and account history | Metadata only; type and opening balance stay immutable | Archive and restore | Permanent delete only when no ledger, reconciliation, payment, or subscription references | Journal history preserved; backup includes account description | Partial; repository and migration tests pass; manual UI validation pending |
| Posted transactions | Income, expense, transfer, opening, adjustment, receivable and subscription postings | List/search; account history; filter by type, account, category and date range | Income/expense/transfer correction posts an atomic reversal and replacement; original journal rows remain unchanged | N/A | Reverse/void supported; destructive delete prohibited; already-reversed records cannot be corrected or reversed again | Original is marked Reversed; linked receivable transactions are excluded from generic correction; connected correction tests pass | Partial; drafts, refunds, type conversion and some specialized postings need dedicated workflows |
| Transaction filters and duplicate | Duplicate opens a prefilled new-entry form | Search covers description, counterparty, source/destination account and category; type/account/category/date filters | N/A | N/A | N/A | Filter boundaries and transfer account association have unit coverage; duplicate and correction forms were smoke-checked on emulator | Partial; saved filters and additional dimensions are not implemented |
| Income and expense categories | Seeded and custom | Management screen; search and archived filter | Rename, icon, color and parent; type remains fixed | Archive and restore; active children must be archived first | Delete only when unused; merge into an active same-type root category | Edits preserve IDs; merge atomically reassigns journal category references without changing amounts/accounts | Partial; repository lifecycle, migration and backup tested; manual UI validation pending |
| Subcategories and income sources | One-level income/expense subcategories; income categories act as custom source names | Category manager and transaction history | Reorganize by changing parent; rename sources | Archive/restore | Merge; safe delete when unused | Same-type parent is enforced; no separate source entity or source-specific analytics yet | Partial |
| Budgets | No | No | No | No | No | No budget persistence/reporting | Incomplete |
| Receivables | Yes | List with derived due/paid status; payment records stored | Due date/counterparty correction UI incomplete | No complete archive lifecycle | Reversal supported for collected payments; cancel/write-off workflow incomplete | Partial collection and reversal are transactional | Partial |
| Accounts payable, debts and personal loans | No complete module | No | No | No | No repayment lifecycle | No dedicated entities or reports | Incomplete |
| Expected income | No | No | No | No | No conversion lifecycle | No planned-income persistence | Incomplete |
| Subscriptions | Create, update and delete repository paths; create screen | List and normalized monthly commitments | UI editing and lifecycle controls incomplete | Pause/resume/cancel controls incomplete | Repository delete path; UI safeguards/history incomplete | Confirmed subscription payments are journaled separately | Partial |
| Savings goals and fund allocations | No | No | No | No | No | No double-count-safe allocation model | Incomplete |
| Savings accounts and time deposits | Existing account types only | Account balances | Interest schedule and maturity management unavailable | Account archive only | Safe unused-account deletion applies | Interest, tax, maturity and manual-credit workflows unavailable | Incomplete |
| Tags, clients, planned payments, templates, projects, saved filters and custom reports | No | No | No | No | No | No persistence model | Incomplete |
| Dashboard preferences and widgets | Theme mode/palette preferences persist; dashboard is derived | Dashboard summaries; Available Money is primary and Net Worth is separate | Theme settings editable; metric ordering/visibility incomplete | N/A | No widget lifecycle | Totals derive from journal and account settings | Partial |
| Backup and restore | Encrypted backup creation | Restore preview and integrity checks | N/A | N/A | Restore replaces records atomically after confirmation | Current database records include account descriptions and category hierarchy/archive state; older schema-2 backups use defaults | Runtime backup/restore test passes for current entities |
| App lock and export privacy | No app lock | Settings only | No PIN/biometric setup | Session lifecycle unavailable | N/A | Automatic Android backup disabled; encrypted portable backup available | Partial |

## Account lifecycle implementation notes

- Account editing is deliberately limited to name, institution, description, icon, color and dashboard inclusion. Account type, currency, opening balance, creation time, ledger balance and reconciliation metadata remain unchanged.
- Account deletion checks, in one Room transaction, for journal-entry and journal-line references, reconciliations, receivable payment destinations and subscriptions. If any are present, the account remains intact and can be archived instead.
- Account creation and archive/restore continue to use Room; a schema migration adds the description field. The encrypted backup serializer includes it and accepts earlier schema-2 backups with an empty description.
- Category edits and merges preserve journal rows and amounts; merge only changes category references and reparents subcategories in one Room transaction.
- Emulator instrumentation passes for account and category lifecycle operations, merge and historical-reference protection, backup/restore, and schema-2→4 migration. UI interactions still require manual validation.
- Transaction correction is available only for income, expense and transfer journal entries. It validates all targets before writing and records the original reversal plus corrected replacement in one Room transaction. The original financial record is never overwritten.
- Duplicate copies transaction details into a new form; it creates no journal rows until the user saves the new entry. Transaction filter controls are available in the journal; date filtering uses a start-inclusive/end-exclusive range.
- The transaction correction and duplicate dialogs plus reversal result were visually checked on `emulator-5554` using synthetic data. Automated connected tests cover correction atomicity, journal preservation, invalid target rejection and receivable exclusion.

## MyMoney design permission and asset scope

The project owner reports receiving permission from the MyMoney owner to use MyMoney assets and design principles. The permission terms and permitted assets are not present in this repository. RalLedger therefore applies general usability principles and retains its own identity; it does not bundle MyMoney artwork, source code or trademarks. Before using specific third-party assets, record the actual approved scope and distribution limits.
