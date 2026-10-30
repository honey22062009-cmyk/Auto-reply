# Local emulator execution evidence

Recorded 2026-10-30 UTC. This is an environment result, not a Samsung or messaging-app compatibility result.

## Environment

- Android Emulator 37.2.12.0; Android 15 / API 35 Google APIs x86_64 image.
- Runtime model: `sdk_gphone64_x86_64`.
- Software CPU emulation (`-accel off`); no `/dev/kvm` is available.
- SwiftShader graphics; display observed through `wm`: 800 × 1280, density 160.
- No Samsung tablet is attached. WhatsApp and Instagram are not installed or tested.

The SDK adb binary cannot create its default directory on this workspace's read-only home mount. A development-only Python `adb-shell` client communicated with the disposable emulator's standard debug ADB endpoint at localhost:5555. This helper is outside the Android project and is not packaged into the app. HOME was preserved.

## Executed attempt

The existing AVD was restarted headless at approximately 02:33 UTC with:

```sh
emulator -avd veil_api35 -no-window -no-audio -no-boot-anim -no-snapshot \
  -accel off -gpu swiftshader_indirect -memory 2048 -cores 2 \
  -skin 800x1280 -camera-back none -camera-front none -port 5554 -feature -Vulkan
```

ADB-protocol shell calls succeeded. Android reported API 35, release 15, and the model above. Zygote, system_server, and the package service were observed starting. The debug APK was copied to `/data/local/tmp/veil-debug.apk` in the disposable emulator.

The first installation attempt exceeded the client's 30-second transport inactivity timeout. A later attempt with a longer timeout returned:

```text
cmd: Failure calling service package: Broken pipe (32)
```

The stock emulator crash buffer recorded this framework failure at 02:39:28:

```text
FATAL EXCEPTION IN SYSTEM PROCESS: main
java.lang.IllegalStateException: Lost network stack. This is not the root cause
of any issue, it is a side effect of a crash that happened earlier.
```

Zygote then restarted. The root cause of the earlier network-stack failure was not established. `sys.boot_completed` had not reached `1`, and the VEIL package was not found. The emulator attempt was stopped after this framework failure; repeatedly retrying software boot would not establish app correctness.

## Verification boundary

Local emulator installation, app launch, screenshots, UI instrumentation, and actual Keystore instrumentation are **not verified** by this attempt. No passing result is inferred from APK compilation or from the emulator accepting ADB connections.

The instrumented suite includes isolated Compose interaction tests, a real Android Keystore envelope test, and `MainActivitySmokeTest` for real app navigation, pause persistence, contact creation/deletion, and theme persistence. The smoke test is explicitly restricted to the disposable Google emulator because it resets that test installation's local history. It captures synthetic empty-app artwork only if execution reaches that step; no such screenshot was produced in this local attempt.

Run on a functioning emulator or suitable dedicated test device using:

```sh
./gradlew connectedDebugAndroidTest
```

An accelerated CI emulator can validate those instrumented checks. Actual WhatsApp/Instagram notification structure, RemoteInput behavior, recipient receipt, and Samsung One UI background behavior still require the owner's [device test guide](DEVICE_TEST_GUIDE.md).

The repository includes a manual [Android device tests workflow](../.github/workflows/device-tests.yml). After repository writes are available, the owner can select **Actions → Android device tests → Run workflow**. It uses an accelerated generic API 35 emulator and uploads test reports and any synthetic screenshot produced. The workflow is supplied as source and was not executed during this session.
