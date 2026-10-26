# Release signing

Release builds can use the application's owner-managed Android keystore without storing signing material in Git.

Provide all four settings through Gradle project properties or environment variables:

| Gradle property | Environment variable | Value |
|---|---|---|
| `ralledgerReleaseStoreFile` | `RALLEDGER_RELEASE_STORE_FILE` | Path to the private keystore file |
| `ralledgerReleaseStorePassword` | `RALLEDGER_RELEASE_STORE_PASSWORD` | Keystore password |
| `ralledgerReleaseKeyAlias` | `RALLEDGER_RELEASE_KEY_ALIAS` | Signing key alias |
| `ralledgerReleaseKeyPassword` | `RALLEDGER_RELEASE_KEY_PASSWORD` | Signing key password |

For local builds, properties may be kept in the user's Gradle configuration (`%USERPROFILE%\.gradle\gradle.properties`). Use an absolute keystore path to avoid relative-path ambiguity. CI should provide the settings as protected secrets. Do not add the keystore or passwords to the repository; `.gitignore` excludes common keystore and local configuration files.

Build with:

```powershell
.\gradlew.bat :app:assembleRelease
```

If no signing settings are supplied, Gradle builds an **unsigned** release APK for build verification. The unsigned APK is not suitable for installation or distribution. Supplying only some settings or pointing to a missing keystore fails the build with a specific configuration error. Release minification remains disabled until the release variant has its own runtime verification.
