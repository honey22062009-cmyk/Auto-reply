# Phase 1 build verification — 2026-10-30 UTC

This is the historical 0.1.0 prototype report. Version 1 results are recorded in [V1_RELEASE_RESULTS.md](V1_RELEASE_RESULTS.md).

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

The local software emulator failed in the stock Android framework before full boot. That local attempt did not verify app launch, screenshots, UI instrumentation, or actual Keystore behavior; see [EMULATOR_RESULTS.md](EMULATOR_RESULTS.md). Subsequent accelerated GitHub emulator results are recorded separately in [CI_DEVICE_RESULTS.md](CI_DEVICE_RESULTS.md). No Samsung performance measurement or recipient receipt is claimed. Actual Keystore checks are separate from repository tests using a fake cipher.

## Repository

Source and Git history were successfully pushed to `https://github.com/honey22062009-cmyk/Auto-reply.git` on 2026-10-30 after write access was restored. The initial GitHub Actions run failed during SDK setup because the setup action requested the obsolete `tools` package; both workflows now explicitly request `platform-tools`. Remote CI outcomes are tracked separately from the successful local verification above.

The corrected [GitHub verification run](https://github.com/honey22062009-cmyk/Auto-reply/actions/runs/40994227430) completed successfully for commit `24df4a6379f25efa003848b1c825163d020fb3c7`: unit tests, lint, debug APK, and instrumentation APK all passed their build gates. The [APK and reports artifact](https://github.com/honey22062009-cmyk/Auto-reply/actions/runs/40994227430/artifacts/12951006046) was uploaded with 14-day retention. This remote build uses its own debug signing key; its APK checksum may differ from the local artifact above. The separate emulator workflow result is documented in `CI_DEVICE_RESULTS.md` when available.

Generated APKs, caches, local toolchain configuration, signing keys, and raw build logs are excluded from source control. The workflow uploads build artifacts after successful verification when it can run in GitHub.
