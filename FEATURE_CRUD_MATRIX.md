# RalLedger CRUD and lifecycle matrix

Updated: 2026-10-23
Branch: `feature/ralledger-v2-live-ready`

This matrix distinguishes a working lifecycle from a create-only screen or repository primitive. “Partial” means one or more requested operations remain unavailable in the app UI or lack verified persistence. A button or repository method alone is not counted as complete.

| Record or feature | Create | View / search | Edit | Archive / pause / restore | Delete / void / close | History and integrity | Status |
|---|---|---|---|---|---|---|---|
| Financial accounts | Yes | Yes; list search and account history | Metadata only; type and opening balance stay immutable | Archive and restore | Permanent delete only when no ledger, reconciliation, payment, or subscription references | Journal history preserved; backup includes account description | Partial; runtime instrumentation pending |
| Posted transactions | Income, expense, transfer, opening, adjustment, receivable and subscription postings | List/search; account history | No in-place edit; accounting-safe correction workflow incomplete | N/A | Reverse supported; destructive delete prohibited by product flow | Reversal links preserve the original journal | Partial |
| Transaction filters and duplicate | N/A | Search by description, counterparty, account and category | N/A | N/A | Duplicate action and date/type filters unavailable | Reversal history retained | Partial |
| Income and expense categories | Seeded categories and custom create primitive | Available to transaction forms | No management UI | No | No safe delete/reassignment UI | Existing journal category IDs are preserved | Partial |
| Subcategories and income sources | No dedicated source model | No source-specific analytics | No | No | No merge/archive workflow | Income categories are not treated as a complete source-management system | Incomplete |
| Budgets | No | No | No | No | No | No budget persistence/reporting | Incomplete |
| Receivables | Yes | List with derived due/paid status; payment records stored | Due date/counterparty correction UI incomplete | No complete archive lifecycle | Reversal supported for collected payments; cancel/write-off workflow incomplete | Partial collection and reversal are transactional | Partial |
| Accounts payable, debts and personal loans | No complete module | No | No | No | No repayment lifecycle | No dedicated entities or reports | Incomplete |
| Expected income | No | No | No | No | No conversion lifecycle | No planned-income persistence | Incomplete |
| Subscriptions | Create, update and delete repository paths; create screen | List and normalized monthly commitments | UI editing and lifecycle controls incomplete | Pause/resume/cancel controls incomplete | Repository delete path; UI safeguards/history incomplete | Confirmed subscription payments are journaled separately | Partial |
| Savings goals and fund allocations | No | No | No | No | No | No double-count-safe allocation model | Incomplete |
| Savings accounts and time deposits | Existing account types only | Account balances | Interest schedule and maturity management unavailable | Account archive only | Safe unused-account deletion applies | Interest, tax, maturity and manual-credit workflows unavailable | Incomplete |
| Tags, clients, planned payments, templates, projects, saved filters and custom reports | No | No | No | No | No | No persistence model | Incomplete |
| Dashboard preferences and widgets | Theme mode/palette preferences persist; dashboard is derived | Dashboard summaries | Theme settings editable; metric ordering/visibility incomplete | N/A | No widget lifecycle | Totals derive from journal and account settings | Partial |
| Backup and restore | Encrypted backup creation | Restore preview and integrity checks | N/A | N/A | Restore replaces records atomically after confirmation | Current database records are included; schema 2 account backups migrate description as blank | Implemented for current entities; runtime test pending |
| App lock and export privacy | No app lock | Settings only | No PIN/biometric setup | Session lifecycle unavailable | N/A | Automatic Android backup disabled; encrypted portable backup available | Partial |

## Account lifecycle implementation notes

- Account editing is deliberately limited to name, institution, description, icon, color and dashboard inclusion. Account type, currency, opening balance, creation time, ledger balance and reconciliation metadata remain unchanged.
- Account deletion checks, in one Room transaction, for journal-entry and journal-line references, reconciliations, receivable payment destinations and subscriptions. If any are present, the account remains intact and can be archived instead.
- Account creation and archive/restore continue to use Room; a schema migration adds the description field. The encrypted backup serializer includes it and accepts earlier schema-2 backups with an empty description.
- Emulator instrumentation passes for metadata edits, preserved ledger values, archive/restore, deletion of an unused account, refusal to delete an account with journal history, and schema-2→3 migration. UI interactions still require manual validation.

## MyMoney design permission and asset scope

The project owner reports receiving permission from the MyMoney owner to use MyMoney assets and design principles. The permission terms and permitted assets are not present in this repository. RalLedger therefore applies general usability principles and retains its own identity; it does not bundle MyMoney artwork, source code or trademarks. Before using specific third-party assets, record the actual approved scope and distribution limits.
