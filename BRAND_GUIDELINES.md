# PesoMind brand guidelines

## Product identity

- **Name:** PesoMind
- **Tagline:** Peace of mind for your money.
- **Interaction philosophy:** Select. Use. Forget.
- **Product:** A personal finance manager, not a bank or professional accounting service.

## Approved logo files

- `brand/pesomind-horizontal.png` — transparent horizontal wordmark for About and recovery-error presentations.
- `brand/pesomind-icon.png` — transparent standalone mark for the launcher and compact app identity.

Preserve transparency and aspect ratio. Do not recolor, stretch, or recreate either supplied logo. The standalone icon is inset in the Android adaptive-icon foreground so launchers can apply their mask without clipping its edges.

Only the full-color icon and horizontal wordmark were supplied. No approved monochrome icon was included, so the Android adaptive icon does not declare a fabricated monochrome layer.

## Color direction

| Role | Color |
|---|---|
| Ocean blue accent | `#00C2FF` |
| Calm blue | `#3B82F6` |
| Deep indigo | `#1E3A8A` |
| Soft cyan | `#4DD0E1` |
| Launch surface | `#EAF6FF` |

Use the bright cyan as an accent. Use sufficiently dark blue text and controls where contrast matters. Existing user-selected light/dark modes and color palettes remain available.

## Android integration and data safety

The application label, launch presentation, navigation header, About section, app-lock copy, widgets, notifications, export names, and newly created backup metadata use PesoMind. The backup reader continues to accept the legacy `RalLedger` app name so previous encrypted backups remain restorable.

The Android application ID and Kotlin namespace remain `com.ralledger`; the Room database filename remains `ralledger_db`. This is a presentation rebrand, with no database migration or data reset.

## Opening presentation

The AndroidX SplashScreen API shows the supplied mark on a pale-blue launch surface across the app's supported Android versions, with a dark surface in system dark mode. The system splash stays visible only while startup recovery is running; it has no fixed branding delay. On first launch, a short native Welcome screen shows the mark, tagline, supporting description, and Get Started action. DataStore records completion once, while an existing database or saved settings mark an upgraded installation as already welcomed. The Welcome screen uses the selected app theme. If startup recovery fails, the retry screen uses the horizontal wordmark. The normal Home dashboard remains the app's destination; this repository does not contain a marketing website.

## Availability and distribution

The app remains in development. Debug APKs are not production-signed and are not presented as a public release. No domain, contact address, social account, or public download URL has been supplied.
