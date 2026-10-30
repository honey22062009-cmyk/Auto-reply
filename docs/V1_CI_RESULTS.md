# Version 1 GitHub build verification

## Verified intermediate revision

The [Android verification run 41062431204](https://github.com/honey22062009-cmyk/Auto-reply/actions/runs/41062431204) completed successfully on 2026-10-30 for commit `b46704a6825d2cdf80811c44deb8cad6649ad408`.

Executed on the Ubuntu GitHub runner with Temurin JDK 17, Android SDK 35, and Build Tools 35.0.0:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease lintRelease --stacktrace
```

- The downloaded HTML test report confirms **203 tests, 0 failures, 0 ignored**, with 33.498 seconds of test time.
- Debug unit tests, Android lint for debug and release, debug APK assembly, instrumentation APK assembly, and release assembly all passed their workflow gates.
- The log records `minifyReleaseWithR8` execution and `BUILD SUCCESSFUL in 4m 21s`.
- [APK and verification reports](https://github.com/honey22062009-cmyk/Auto-reply/actions/runs/41062431204/artifacts/12981046675) were uploaded as `veil-debug-and-verification`, with the configured 14-day artifact retention. That archive includes debug/test APKs and reports; it does not include a separately signed private release APK.

This result applies to the named revision. The subsequent conversation-context safety guard requires its own final build/test result; this intermediate run must not be presented as verification of later source changes.

## Verification boundaries

Host-side tests use synthetic notifications, fake reply dispatch, and fake AI transport. Successful CI does not establish actual WhatsApp/Instagram notification formats, supported reply actions, recipient receipt, Samsung One UI behavior, or paid OpenAI account access and generation quality. No live provider request or provider spending is implied by this run.

The separate accelerated emulator workflow executes Android UI and Keystore checks; its revision-specific outcome is recorded separately. A compiled instrumentation APK is not itself a passing instrumentation result. Release assembly/R8 success is also distinct from verification of the private release signing identity and installation on the owner's tablet.
