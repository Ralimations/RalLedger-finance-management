# PesoMind

**Peace of mind for your money.**

PesoMind is an offline-first Android personal finance manager for organizing accounts, transactions, budgets, receivables, subscriptions, savings goals, and financial reports.

## Android app

The Android project is in `TestApplication/`. From PowerShell:

```powershell
Set-Location TestApplication
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `TestApplication/app/build/outputs/apk/debug/app-debug.apk`. Debug builds are for development and are not production-signed.

The public open-beta APK is `releases/RalLedger-debug.apk` (PesoMind 2.0-beta1, version code 3). It is signed with the Android debug certificate so it can update the earlier APK distributed from this repository. It is not a production-signed release; see [release signing](RELEASE_SIGNING.md) before using it as a production channel.

The public app name is PesoMind. The existing Android application ID and Kotlin namespace remain `com.ralledger`, and the Room database remains `ralledger_db`, so the rebrand does not change the install identity or local ledger.

The app uses a native AndroidX splash screen while startup recovery runs. New installations get a one-time welcome screen; existing installations skip it and continue through their configured app-lock flow. No website or WebView is part of the app.

See [Brand guidelines](BRAND_GUIDELINES.md), [Phase II progress](DEVELOPMENT_PROGRESS.md), and [release signing](RELEASE_SIGNING.md).

This repository does not include a marketing website; the native Android app and public beta APK are the project deliverables.
