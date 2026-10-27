# PesoMind 2.0 beta 1

- **Version:** 2.0-beta1 (version code 3)
- **Minimum Android:** 8.0 (API 26)
- **APK:** [`RalLedger-debug.apk`](RalLedger-debug.apk)
- **SHA-256:** `9B046AB5F53E36FF3FC990278F163BB144B6E4CCE6F4EFC97ACBA5ADB695BFF1`

## What's new

- Opt-in reminders for recurring income and expense schedules. Reminders start off and are enabled per schedule from Planning → Recurring transactions → Edit.
- A reminder opens the current occurrence in the existing transaction review form. No recurring transaction is posted until the user confirms it.
- Pausing a schedule, changing its next occurrence, or turning reminders off cancels or refreshes its pending alarm.
- Existing schedules and older backups default to reminders off. Encrypted backups preserve the new setting.

## Install and signing

This beta APK is signed with the Android debug certificate used by previous APKs in this repository. Its certificate SHA-256 is `fabee57ffeb8f0ab41f1f3cd9ab03386c84a65d653f800b80ade78320ce14816`.

It can update an earlier repository APK signed by that same certificate. Android will reject an update over an installation signed with a different key. Back up financial data before replacing any differently signed installation; uninstalling an app removes its local data.

This is an open beta build, not a production-signed release. The Release variant assembles unsigned until the owner-managed keystore is configured.
