# Build toolchain

VEIL is an independent Android project with application ID `dev.veil.app`.

The selected build uses Gradle 8.11.1, Android Gradle Plugin 8.9.2,
Kotlin/Compose compiler plugin 2.1.20, KSP 2.1.20-1.0.32,
Compose BOM 2025.04.01, Room 2.7.1, and DataStore 1.1.4.
The compile/target SDK is 35; minimum API is 28. Java/Kotlin bytecode targets 17.
These versions are pinned. They are a compatible stable baseline, not a claim
to be the newest versions available.

## This build workspace

- Temurin JDK 17.0.21+9 for compilation and tests; Java 17 is also used in CI.
- The preinstalled OpenJDK 21 was runtime-only and could not compile this project.
- Android SDK: `/workspace/android-sdk`
- Android command-line tools: revision 12.0 (archive 11076708).
- Android platform: API 35; build tools: 35.0.0.
- Android platform-tools: 37.0.1.
- Gradle user cache: `/workspace/gradle-cache`
- Gradle distribution: `/workspace/build-tools/gradle-8.11.1`
- `local.properties` and `toolchain.env` are workspace-specific and ignored.

Run from `/workspace/veil`:

```sh
source toolchain.env
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug assembleDebugAndroidTest
```

Networked tools in the managed environment need an execution call with network
access enabled. The inherited proxy remains in use. Java is configured with a
copy of the system trust store plus the environment proxy CA; TLS verification
is enabled. SDK licenses were accepted during installation. No account
credentials are needed to download the public Android dependencies.

The Gradle distribution SHA-256 is pinned in wrapper properties:
`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.
The downloaded archive matched this upstream checksum.

## A developer machine

Install Android Studio or JDK 17 plus Android command-line tools. Install
`platform-tools`, `platforms;android-35`, and `build-tools;35.0.0`; accept the
Android SDK licenses. Let Android Studio create `local.properties`, or set
`ANDROID_HOME` to the SDK path. Use the checked-in Gradle wrapper.
Do not copy this cloud workspace's proxy or trust-store paths.

## Device and emulator boundary

This environment has no `/dev/kvm`, so hardware-accelerated Android emulator
testing is unavailable. A real Samsung tablet is not attached. Unit tests and
Robolectric tests are host-side checks and do not establish WhatsApp/Instagram
or Samsung device compatibility. Instrumented tests require an attached device
or a functioning emulator via `./gradlew connectedDebugAndroidTest`.
The installed `adb` also cannot initialize in this restricted workspace because
its default `/home/agent/.android` directory is read-only. The software emulator attempt and exact runtime results are recorded in
[EMULATOR_RESULTS.md](EMULATOR_RESULTS.md). No Samsung compatibility is claimed.

The CI workflow builds and uploads debug artifacts and test reports. It is
included in the source; no GitHub repository has been created or published.
