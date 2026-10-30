# Phase 1 build verification — 2026-10-30 UTC

Executed from the standalone VEIL project:

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --console=plain
```

Result: **BUILD SUCCESSFUL** in 46 seconds on the final run.

- Host tests: **76 passed**, zero failures, zero skipped. These include pure domain tests and Robolectric/Room/DataStore tests with synthetic notifications and a fake repository cipher/dispatcher.
- Debug APK: built; package `dev.veil.app`, version `0.1.0`, min API28, target API35.
- Debug instrumentation APK: built. Building the tests does not establish execution.
- Android lint: passed with no errors. Warnings cover newer dependency versions and Compose style/performance recommendations; pinned versions are a compatible baseline.
- `apksigner verify`: executed successfully on the final debug APK.
- No Internet permission appears in the APK manifest. AndroidX adds an app-scoped signature permission for non-exported dynamic receivers.

APK path: `app/build/outputs/apk/debug/app-debug.apk`

Size: **61,530,447 bytes** (61.5 MB).

SHA-256:

```text
b79c3595d936c71b6224c841f74699e9e81f1194d670d2d33fa3417768d124f2
```

## Runtime boundary

No Samsung Galaxy Tab A9+ is attached. Actual WhatsApp/Instagram detection, reply-action availability, dispatch and receipt remain unverified. See [DEVICE_TEST_GUIDE.md](DEVICE_TEST_GUIDE.md).

The software emulator failed in the stock Android framework before full boot. No app launch, UI screenshot, instrumentation pass, real Keystore result, performance measurement, or recipient receipt is claimed. See [EMULATOR_RESULTS.md](EMULATOR_RESULTS.md). The actual Keystore test is separate from repository tests using a fake cipher.

## Repository

Source and Git history were successfully pushed to `https://github.com/honey22062009-cmyk/Auto-reply.git` on 2026-10-30 after write access was restored. The initial GitHub Actions run failed during SDK setup because the setup action requested the obsolete `tools` package; both workflows now explicitly request `platform-tools`. Remote CI outcomes are tracked separately from the successful local verification above.

Generated APKs, caches, local toolchain configuration, signing keys, and raw build logs are excluded from source control. The workflow uploads build artifacts after successful verification when it can run in GitHub.
