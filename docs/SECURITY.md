# VEIL Phase 1 security and privacy notes

This is a private compatibility build, not a completed security certification. The safeguards below are scoped to Phase 1. Actual-device notification behavior remains unverified until the owner performs the [device test guide](DEVICE_TEST_GUIDE.md).

## Permission and data boundaries

- VEIL uses the separate package `dev.veil.app`. It does not use Strict Blocker's configuration, repository, signing identity, payments, or Firebase project.
- The owner grants notification-listener access through Android settings. This is broad system access: Android can expose sensitive notifications to a listener. VEIL filters to the supported, explicitly enabled package names before recording content, but that application-level filter does not reduce the underlying Android permission's breadth.
- Phase 1 does not request Internet, Accessibility Service, SMS, contacts, microphone, camera, or all-files access. It does not sign in to WhatsApp, Instagram, ChatGPT, or an AI provider. There are no account passwords or API keys to enter.
- Only notification-derived information is available. Hidden previews, unavailable sender information, complete conversation history, and verified delivery receipts cannot be inferred.
- Diagnostic exports are owner-initiated and must omit message text, sender labels, conversation identifiers, raw notification extras, and credentials. Review any exported file before choosing a share destination.

## Local persistence

Room holds bounded notification observations, event records, and explicitly entered priority labels. DataStore holds local preferences. Sensitive text fields use AES-GCM envelopes backed by a non-exportable Android Keystore key. Authentication failure or an unavailable key returns no plaintext rather than guessing content. This is **field encryption**, not an encrypted Room database: platform identifiers, timestamps, state flags, and lookup/fingerprint metadata may remain visible to someone who can access the app's private files.

The Keystore key is not user-authentication-bound in this milestone, so background capture can encrypt data without repeatedly prompting the owner. Hardware-backed key storage is device-dependent and is not claimed without measurement. Android app sandboxing and the device's lock and storage protections still matter. A rooted, compromised, unlocked, or debug-authorized device can expose runtime data. A debug APK is intended for testing, not high-assurance private deployment.

Default retained history is one day; the settings limit is one to seven days, with bounded record counts. Purging happens when the application processes/maintains data; it is not a promise that an idle or force-stopped device erases data at an exact deadline. The owner can delete history and delete contact labels. Deletion removes application records; it does not claim forensic secure erasure of flash storage, Android's own notification history, the source messaging apps, recipient copies, or an exported report.

## Controlled sending

There is no automatic send path in Phase 1. A reply test requires the owner to select an observed message and confirm a consenting test contact. The fixed test text is recognizable and does not pretend to answer the incoming message. Copying text or opening the source app is available when supported dispatch is absent; the owner verifies the recipient in that app.

The sending boundary must recheck all of the following immediately before using a live reply action:

- Onboarding is complete, capture is not paused, the platform is enabled, and notification access/listener connection are valid.
- A usable, active, non-group message has a visible sender and preview.
- The notification still matches the reviewed observation; a replacement, removal, local-user reply, or stale process handle invalidates it.
- A supported free-form reply action exists, belongs to the originating app, and does not require bypassing device authentication or mutability restrictions.
- The observation has not already had a test attempt. Concurrent taps must serialize into at most one attempt.

Persisting an attempt before dispatch deliberately avoids an automatic retry after an uncertain process failure. A failed or uncertain test requires a fresh incoming test message and a new owner action. This trades convenience for protection from duplicate real messages.

An accepted Android `PendingIntent` is recorded as **Accepted by Android**. It is never labeled delivered. Receipt can only be recorded as an owner's independent report in this milestone; there is no delivery-receipt integration.

## Diagnostics and logs

Do not log message content, sender names, full notification bundles, reply text, credentials, or raw exception payloads. Sanitized app/version, device-capability, connection, count, and dispatch-stage information is sufficient for the compatibility report. Even that information can identify a device configuration, so export remains explicit.

Android bugreports and general-purpose logcat capture are outside VEIL's sanitized export and can include unrelated sensitive data. Inspect them before sharing. Never upload private conversations to a code repository or issue tracker to reproduce a notification parsing problem; reproduce it with a consenting test message instead.

## Remaining work before later phases

Priority labels do not enable automation in this build. Future automatic replies require stable identity handling, ambiguity rejection, persistent limits/cooldowns, quiet hours, sensitive-message and uncertainty escalation, conservative context handling, and explicit per-contact enablement. Notification metadata cannot guarantee that the owner has not replied through another device.

Future cloud engines need a separate content-sharing consent, secure credential lifecycle, provider-specific retention disclosure, network timeouts/cancellation, rate and spending controls, and supported API/OAuth authorization. A ChatGPT subscription is not an implicit credential. Future local models need license, memory, inference-quality, thermal, and battery validation on the actual tablet. Neither engine is simulated in Phase 1.

Before a production private release: complete device compatibility tests, verify backup exclusion on the target One UI version, audit the merged manifest and dependencies, test screen/clipboard exposure, confirm crypto behavior with instrumented tests, and create a dedicated release signing key outside source control. Keep that key for future updates; replacing it can require uninstalling the app and losing local data.

## Security-reporting checklist

When reporting an issue privately to the developer, include VEIL version, Android/One UI version, platform version, reproducible harmless steps, expected behavior, actual behavior, and a reviewed sanitized diagnostic file. Do not include real message text, contact names, account tokens, OTPs, private keys, or an unrestricted device bugreport.
