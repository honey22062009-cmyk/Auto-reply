# Software emulator validation

This environment has no attached Samsung Galaxy Tab A9+ and no `/dev/kvm`.
An Android 15 / API 35 Google APIs x86_64 image is available. The installed
Android emulator is 37.2.12.0 (build 16428233). A headless emulator was started
with software CPU emulation and SwiftShader graphics. This is a generic
emulator, not a simulation of Samsung One UI or messaging-app compatibility.

The emulator uses an 800 × 1280 display and the image raises RAM to 2560 MB.
These are test settings, not a claim about the user's actual tablet variant.

The SDK's adb 37.0.1 executable aborts when creating its hard-coded
`$HOME/.android` directory because the home mount is read-only. The supported
`ANDROID_USER_HOME` and `ANDROID_PREFS_ROOT` settings do not redirect this adb
version's directory. A scoped filesystem grant did not change that mount.
`HOME` was preserved.

For this disposable development emulator, the Python `adb-shell` 0.4.4 client
communicates directly with the emulator's debug ADB endpoint at localhost:5555.
The emulator's debug image accepts the standard ADB connection. No real device,
account, or app authentication is bypassed. Development-only dependencies are
under `/workspace/adb-python`; nothing is included in the APK.

Workspace helper:

```sh
python /workspace/emulator_control.py shell 'getprop sys.boot_completed'
python /workspace/emulator_control.py install /absolute/path/to/app-debug.apk
python /workspace/emulator_control.py shell 'am start -W -n dev.veil.app/.MainActivity'
python /workspace/emulator_control.py screenshot /workspace/veil/docs/emulator.png
```

These local-endpoint calls require the execution tool's network access. No
message data or credentials are involved. Running an instrumented test suite
requires installing both APKs and invoking `am instrument` with the test runner.

## Current evidence

The software emulator starts, accepts ADB-protocol connections, and reports
Android 15 and model `sdk_gphone64_x86_64`. Zygote, SurfaceFlinger, and
system_server were observed running. Boot is slow under CPU emulation.
Application installation, launch, and instrumentation results are recorded
below only after execution.

The resumed local attempt and its framework boot failure are recorded in
[EMULATOR_RESULTS.md](EMULATOR_RESULTS.md). It did not verify installation,
launch, screenshots, or instrumented tests.
