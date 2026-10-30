# VEIL

A standalone Android messaging companion built with Kotlin, Jetpack Compose, Room, and supported Android notification APIs. Application ID: `dev.veil.app`. VEIL is independent of Strict Blocker.

## Version 1.1: separate platform people

The **Priority** screen now has separate WhatsApp and Instagram sections, each with its own Add action and concise person cards. For WhatsApp you can save the name used on your tablet, an optional country-code phone number, and a different notification sender when needed. For Instagram you can save a display name, username, optional numeric user ID, and an exact notification sender if different. Optional fields are local, encrypted references; VEIL never queries private platform APIs to verify them.

**Important:** Android notifications do not promise a WhatsApp phone number, an Instagram username, or an Instagram numeric user ID. Saving these hints does **not** prove recipient identity or make auto-sending possible. VEIL still requires the app-exposed stable notification identity, a consented fixed test with receipt reported by the owner, and opt-in both per person and globally. Editing any identity hints disables automatic replies and requires a fresh notification binding. Users can edit conservative reply limits under **Advanced rules** without cluttering the default person view.

Existing data is migrated with Room v2→v3, retaining saved contacts, settings, history, and the duplicate-prevention ledger. This update uses versionCode 101; update installs require the same Android signing key as the installed VEIL build.

## Version 1

VEIL reads accessible notifications from the official WhatsApp and Instagram Android apps that you explicitly enable. It offers local reply templates, optional OpenAI drafts, manual review and editing, a compatibility lab, and deliberately restricted contact automation. This is a notification-derived view, not a replacement for either app's complete inbox.

The interface includes a floating glass-style navigation dock, tablet layouts, light/dark/system appearance, Rose/Coral/Plum/Amber/Silver accents, glass intensity, reduced motion, and **Mote**, an optional interactive companion that guides you to useful app screens. Mote is deterministic guidance, not an AI chatbot. Glass rendering uses supported effects and layered translucency; it does not claim native system-level backdrop sampling.

### Sending stays under your control

Automatic sending is off by default. Enabling it requires both the global switch and explicit contact authorization, a stable recipient identity actually exposed by Android notification metadata, and a successful fixed compatibility-test dispatch whose receipt you separately report. A visible sender name alone cannot authorize automation. Binding a contact does not automatically enable it.

Even after configuration, unattended sending is limited to recognized, self-contained greetings and conservative greeting drafts. Sensitive requests, factual questions, commitments, unknown content, groups, stale messages, missing actions, ambiguous identities, and generator review warnings require personal review or are blocked. Cooldowns, quiet hours, conversation/day limits, global caps, durable deduplication, and repeated-failure suspension remain active. Detection and reply generation never bypass these checks.

Android accepting a reply `PendingIntent` does **not** prove receipt. Actual WhatsApp and Instagram capabilities must be tested separately on the installed app/device versions. If no supported reply action is exposed, edit/copy the draft and open the messaging app to send manually. Instagram direct-reply support is not assumed.

### Reply engines and style

- **Offline templates:** deterministic English, Hindi, and Hinglish suggestions. This is not a downloaded local language model.
- **Optional OpenAI:** your own encrypted API key, explicit cloud-sharing consent, configurable model/output limit, optional limited context, and reported token usage. Provider billing is separate; no cost estimate is fabricated.
- **Writing preferences:** language, warmth, humor, emojis, length, preferred/avoided phrases, and examples you explicitly supply. Templates support a limited subset; cloud generation receives selected preferences/examples only after consent. A handful of examples does not establish complete personality learning.

Cloud generation transmits selected message content to OpenAI. It is not completely local or free, and a ChatGPT subscription is not an API credential. VEIL does not extract credentials from or automate the ChatGPT app. See [reply-engine details](docs/AI.md).

## Build

Use JDK 17, Android SDK 35, and Build Tools 35.0.0. Open the project in Android Studio or configure `ANDROID_HOME`, then run the checked-in wrapper:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug assembleDebugAndroidTest
```

A successful debug build writes `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds source and uploads APKs/reports. Source presence does not establish that a particular revision passed. Consult [build verification](docs/BUILD_VERIFICATION.md) for executed results.

For an attached emulator or dedicated test device:

```sh
./gradlew connectedDebugAndroidTest
```

The manual Android device-tests workflow uses a generic accelerated API 35 emulator. Emulator checks do not establish Samsung, WhatsApp, Instagram, or recipient-delivery compatibility.

## Install and start

1. Download an APK from a verified build. Permit installation from the specific source you use, then open it.
2. Complete onboarding and explicitly select the supported messaging apps VEIL may inspect.
3. Grant notification access through Android Settings. A sideloaded installation may first require **App info → menu → Allow restricted settings**.
4. Open the Compatibility Lab and follow the [tablet test guide](docs/DEVICE_TEST_GUIDE.md) with a consenting one-to-one test contact.
5. Review suggestions in Inbox. Configure AI Studio if you want optional cloud generation. Configure and test each priority contact before deliberately enabling its automation.

The global pause stops capture and blocks replies. Individual contacts can be paused independently. Android and Samsung background restrictions can disconnect a listener; uninterrupted execution is not guaranteed.

## Privacy and updates

Sensitive message fields, drafts, contact instructions, writing examples, and the optional API key use Android Keystore-backed field encryption. Structured metadata is not a fully encrypted database. History defaults to one day and can be set to one, three, or seven days. Deleting visible history retains a content-free dispatch ledger for up to 30 days to protect against duplicate sends and preserve limits. Contact labels remain until removed.

Settings export excludes API keys, contacts, message history, the dispatch ledger, cloud consent, and automation authorization. It includes your supplied writing examples; choose its destination accordingly. Sanitized diagnostics exclude private message/style content and credentials. Backups are excluded, and the activity uses `FLAG_SECURE`. Read the [security notes](docs/SECURITY.md) for exact boundaries.

VEIL requests Internet permission for the optional HTTPS AI integration. It requests no Accessibility Service, contacts, SMS, microphone, camera, or all-files permission. There is no central message server, advertising SDK, or analytics service.

Use the same application ID and signing key for future updates. Changing signing keys usually requires uninstalling and losing local data. Clean CI debug builds may use different keys, so debug artifacts are not a durable release-update channel. Keep a dedicated private signing key outside source control. Never commit API keys, signing secrets, real conversations, or device backups.

## Documentation

- [Version 1 features and limits](docs/V1_FEATURES.md)
- [AI engines and provider data](docs/AI.md)
- [Platform research](docs/PLATFORM_RESEARCH.md)
- [Tablet compatibility test guide](docs/DEVICE_TEST_GUIDE.md)
- [Security and privacy](docs/SECURITY.md)
- [Build toolchain](docs/TOOLCHAIN.md)
- [Build verification](docs/BUILD_VERIFICATION.md)
- [Recorded emulator results](docs/CI_DEVICE_RESULTS.md)

Live OpenAI account access, paid generation quality, actual tablet performance, Samsung background behavior, and messaging-app compatibility require their own verification. No live provider request or spending is implied by the implemented integration or synthetic tests.
