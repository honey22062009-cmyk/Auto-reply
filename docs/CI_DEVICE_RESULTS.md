# Android instrumentation on GitHub Actions

This records the historical 0.1.0 prototype. See [V1_RELEASE_RESULTS.md](V1_RELEASE_RESULTS.md) for Version 1 verification.

Executed 2026-10-30 UTC on commit `24df4a6379f25efa003848b1c825163d020fb3c7`.

- Workflow: [Android device tests](https://github.com/honey22062009-cmyk/Auto-reply/actions/runs/40994283517).
- Result: **success**.
- Environment: Ubuntu GitHub runner with KVM; generic Android 15 / API 35 Google APIs x86_64 emulator using the Pixel C hardware profile.
- Command: `./gradlew connectedDebugAndroidTest --stacktrace`.
- Instrumentation: **8 tests passed, 0 skipped, 0 failed, 0 errors**. Confirmed from the downloaded JUnit XML (`TEST-emulator-5554 - 15-_app-.xml`), including the real-application smoke test, which ran for 13.304 seconds. The log starts eight tests at 02:59:36 UTC, finishes eight tests at 03:00:13 UTC, and records `BUILD SUCCESSFUL` at 03:00:15 UTC.

The executed suite contains six Compose tests for accessible dock navigation, dark mode with large text, explicit test-reply consent, consent reset for a different notification, contact form validation, and default onboarding platform opt-outs. One test exercises actual Android Keystore encryption with randomized authenticated envelopes, tamper rejection, and decryption by a new cipher instance. One real-application smoke test exercises app startup, navigation, persisted observation pause, synthetic contact creation/deletion, and theme persistence. No messaging account or real recipient is used.

The smoke test temporarily clears `FLAG_SECURE` only on its disposable test window to capture an empty, synthetic dark Home screen, verifies PNG compression, then restores it. That assertion passed, but the subsequent workflow screenshot pull returned `No such file or directory`. No screenshot is present in the downloaded artifact, and no visual review is claimed. The app may have been cleaned up by the connected-test runner before the later pull; that cause was not independently verified. Test reports and APKs are retained in the `veil-instrumentation-evidence` workflow artifact.

## Scope

This is executed emulator evidence. It does not verify a Samsung Galaxy Tab A9+, One UI background behavior, WhatsApp or Instagram notification structures, supported RemoteInput actions from either app, actual test-reply dispatch to a messaging app, or recipient receipt. Follow the [device compatibility guide](DEVICE_TEST_GUIDE.md) on the real tablet with a consenting test contact.

The earlier local software emulator failed in the Android framework before app installation. That separate environment failure is documented in [EMULATOR_RESULTS.md](EMULATOR_RESULTS.md); it does not invalidate this completed accelerated CI run.
