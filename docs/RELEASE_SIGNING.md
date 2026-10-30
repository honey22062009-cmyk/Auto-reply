# Private release signing

VEIL version 1 uses application ID `dev.veil.app`, version name `1.0.0`, and version code `100`.

A new signing identity was generated for VEIL alone. It does not reuse the debug certificate, Strict Blocker signing material, or any other project configuration. The PKCS12 store contains an RSA 3072-bit key with alias `veil-v1`.

## Local signing material

In this development workspace only:

- Keystore: `/workspace/veil-release-secrets/veil-v1.p12`
- Password file: `/workspace/veil-release-secrets/keystore-password.txt`
- Directory permissions: owner only (`0700`); both files: owner read/write (`0600`).

The password was generated with a cryptographic random generator. `keytool` read it from the protected file; the password was not placed in command arguments or printed. The keystore is encrypted, but its password file is a separate local secret and must be protected accordingly. Workspace file permissions are not an external secret-management service or a guarantee against a compromised development machine.

These files are outside the Git repository. Do not commit them, include them in source archives or APK downloads, attach them to a GitHub release, or copy them into ordinary build logs. No signing material was uploaded as a repository secret or release asset.

## Build a signed release

The Gradle release signing configuration is enabled only when all three environment variables are present:

- `VEIL_KEYSTORE_PATH`
- `VEIL_KEYSTORE_PASSWORD`
- `VEIL_KEYSTORE_ALIAS`

Set the path and alias to the owner's private files and alias. Supply the password through a local secret manager or a process environment read from the protected file, without displaying it or committing it. Then run:

```sh
./gradlew assembleRelease
```

The application uses R8 code optimization and resource shrinking. Room and Compose dependencies provide their required consumer rules; VEIL does not keep every class or every Material icon. The extended icon dependency is needed by the current source UI, and unused icon code can be removed by R8 in the release build.

Without signing environment variables, CI can build `app-release-unsigned.apk`; it must not be presented as a signed, installable final release. A successful signed build creates `app/build/outputs/apk/release/app-release.apk`. Confirm the actual artifact and verify it with the Android SDK's `apksigner verify --print-certs` before delivery. A build is not considered verified just because this configuration exists.

## Backups and future updates

The owner needs a secure offline backup of the keystore and its password before relying on this signing identity for future installations. Back up the encrypted keystore and store the password separately in an appropriate password manager. A cloud development workspace is not a durable signing-key backup.

Future APK updates must use the same application ID and signing identity, with an increased version code. Losing the key can prevent updates and require an uninstall, which removes local message history, styles, contacts, and API credentials. A release signed with this new identity cannot update an existing Phase 1 debug installation directly; Android normally requires uninstalling that debug build first. Export user-owned settings before doing so.

The certificate fingerprint is public identity information; the private key and its password are not. Keep the private signing material out of public reports. Signing confirms the APK's publisher identity and integrity; it does not certify Samsung, WhatsApp, Instagram, AI-provider compatibility, or production security.
