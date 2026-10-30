# VEIL

A standalone, private Android messaging companion built with Kotlin and Jetpack Compose. Application ID: `dev.veil.app`. This project is independent of Strict Blocker.

## Current milestone: Phase 1

This compatibility build inspects explicitly enabled WhatsApp and Instagram notifications using Android's notification listener. It displays available sender/preview information and supported reply actions, retains bounded local history, and offers an explicit, user-confirmed fixed test reply for a consenting one-to-one test contact.

**Automatic replies and AI generation are not enabled in this milestone.** The template engine is deterministic and requires no network. Priority contact labels can be saved locally; saving a label does not authorize automation.

Features include onboarding, Home, notification-derived Inbox, local priority labels, Activity, Settings, a Compatibility Lab, light/dark themes, a floating translucent navigation dock, tablet layouts, reduced motion, encrypted sensitive text fields, configurable retention, history deletion, and sanitized diagnostic export.

Glass rendering is a layered visual approximation with supported decorative blur. It does not claim system-level backdrop sampling.

## Build

Use JDK 17 and Android SDK 35 with Build Tools 35.0.0. Open this directory in Android Studio, or configure `ANDROID_HOME` and run:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug assembleDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` only after a successful build. GitHub Actions runs the same verification and uploads the debug APK, instrumentation APK, and reports as workflow artifacts. Build results are documented separately; source presence does not establish a passing build.

For connected device tests:

```sh
./gradlew connectedDebugAndroidTest
```

## Install and test

1. Download a verified debug APK from a successful GitHub Actions run or your local build.
2. On the tablet, permit installation from the specific file/browser source you use, then open the APK.
3. Complete onboarding and explicitly select which messaging apps VEIL may inspect.
4. Open notification access settings and enable VEIL. Android may require **App info → menu → Allow restricted settings** for a sideloaded app.
5. Follow the [device test guide](docs/DEVICE_TEST_GUIDE.md) with a consenting test contact.

Notification detection, reply-action availability, Android dispatch acceptance, and recipient receipt are different outcomes. A successful PendingIntent call is never proof of delivery. Actual WhatsApp/Instagram behavior must be checked on the installed app and Android versions. Instagram direct-reply support is not assumed. Unsupported actions use copy/open-app fallback.

## Privacy and signing

Phase 1 requests no Internet, Accessibility, contacts, SMS, microphone, or camera permission. Sensitive stored text uses Android Keystore-backed AES-GCM field encryption; this is not full database encryption. Backups are excluded, and the activity uses `FLAG_SECURE`. Android notification access itself is broad; read the [security notes](docs/SECURITY.md).

Debug builds are for evaluation. Keep a dedicated release signing key outside the repository for durable private distribution. Updates must use the same application ID and signing key; changing keys usually requires uninstalling and losing local data. CI debug signing keys may differ across clean runners, so CI artifacts are not a stable release update channel. Never commit keys, real conversation data, API credentials, or device backups.

## Documentation

- [Platform research and supported API boundaries](docs/PLATFORM_RESEARCH.md)
- [Samsung tablet compatibility test guide](docs/DEVICE_TEST_GUIDE.md)
- [Security and privacy](docs/SECURITY.md)
- [Build toolchain](docs/TOOLCHAIN.md)
- [Build verification](docs/BUILD_VERIFICATION.md)
- [Accelerated emulator test results](docs/CI_DEVICE_RESULTS.md)

The remaining milestones cover explicit per-contact automation, safety/rate rules, modular AI engines, writing style, and measured device reliability. These capabilities are not simulated in Phase 1.
