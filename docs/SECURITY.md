# VEIL Version 1 security and privacy

These notes describe implemented boundaries, not a security certification or an assertion that an untested device/provider works. Read the revision-specific [verification record](BUILD_VERIFICATION.md) and perform the [device test guide](DEVICE_TEST_GUIDE.md) on the actual tablet.

## Permissions and account boundaries

VEIL uses application ID `dev.veil.app`, separate from Strict Blocker and its signing/configuration/accounts. It uses Android's supported `NotificationListenerService`, notification actions, and `RemoteInput`. It does not collect WhatsApp/Instagram passwords, inspect their private storage, use private APIs, scrape web sessions, or automate their screens.

Android notification access is broad. VEIL filters to supported, explicitly enabled packages before examining their extras; that application filter does not narrow Android's underlying permission. Hidden previews and Android redaction are respected. Obvious credential/OTP-related preview content is not retained for drafting, but the guard is a conservative heuristic rather than proof that every secret can be recognized.

Internet permission supports optional OpenAI HTTPS requests. The default template engine uses no network. There is no central message-collection server, analytics service, advertising SDK, or tracking service. No Accessibility Service, SMS, contacts, microphone, camera, or all-files permission is requested. The installed ChatGPT app is not a credential source or an unofficial background API.

## Stored data

Room stores bounded notification observations, generated drafts, contact rules, activity events, and a separate dispatch ledger. DataStore stores preferences and encrypted writing-style content. The optional API key is stored only as an AES-GCM envelope in private preferences. Sensitive text fields use a non-exportable Android Keystore key.

This is **field encryption**, not whole-database encryption. Package names, timestamps, hashed identities/fingerprints, counters, dispatch kinds, and state flags remain structured metadata. The key is not user-authentication-bound, allowing background encryption without repeated prompts. Hardware-backed key storage is device-dependent. A rooted, compromised, debug-authorized, or unlocked device may expose runtime data. No code-level control replaces the owner's device security.

The default visible-history retention is one day, with one/three/seven-day settings and bounded record counts. Purging occurs during normal maintenance, app foreground work, or processing; force-stop/idle conditions do not promise erasure at an exact wall-clock deadline. Deleting history clears retained messages, drafts, and activity plus live reply handles. It retains a **content-free dispatch ledger for up to 30 days** so replayed callbacks cannot resend previously attempted messages or reset reply caps. Contact deletion also does not erase this safety metadata. Contact labels and writing preferences remain until explicitly removed or the app's storage is cleared.

Deletion is not forensic secure erasure of flash, source-app content, Android notification history, recipient copies, or exported files. Android backup/data transfer are excluded in the manifest/configuration. The activity uses `FLAG_SECURE` to restrict screenshots and recent-app previews; this is not a guarantee against all capture mechanisms on every device. Clipboard contents copied by the owner remain subject to Android and other-app behavior.

## Manual sending and compatibility tests

A normal reply requires review of the exact recipient and edited text and explicit confirmation in the UI. A recognizable compatibility-test reply separately requires a consenting test contact. Copy/open-app fallback remains available when an action is absent. Group replies, missing sender/preview information, expired notifications, disconnected access, locked devices, unsupported actions, and invalid text are rejected at the sending boundary.

The platform re-reads the active notification immediately before dispatch, checks the same fingerprint and supported mutable free-form reply action, validates originating-app ownership, and rechecks stop controls immediately before IPC. Raw PendingIntents and notification keys remain memory-only. Removal, replacement, outgoing-local-user updates, disconnects, pause, and process loss invalidate their authority.

The repository records a durable attempt **before** IPC. Manual, test, and automatic sends share at-most-once protection. A failed or interrupted dispatch is never retried automatically; an uncertain result stays uncertain rather than implying either delivery or safe retry. Sending through another linked device cannot always be observed, so notification access never guarantees knowledge of all owner replies.

Android accepting a PendingIntent is recorded as **Accepted by Android**. Receipt is available only as an owner-supplied report, not independent verification. The app never upgrades Android acceptance into delivery proof.

## Restricted automation

Both global automation and each contact's automation are off by default. An explicitly configured contact must be bound to opaque stable identity metadata actually supplied by the notification platform; a displayed sender name alone is insufficient. A successful fixed **TEST** dispatch and separately reported recipient receipt are required before the bound contact can be enabled. A normal manual reply receipt is not a compatibility credential, and binding itself keeps automation disabled.

Automatic sends additionally require a fresh live non-group message, usable preview, matching unambiguous identity, supported current action, complete local reply-limit history, safe generator result, and successful conservative policy checks. Reconnected/historical notification replays do not authorize unattended sends. Only recognized self-contained greetings and narrow greeting drafts may pass. Sensitive subjects, factual uncertainty, promises, commitments, unknown instructions, missing context, and unsupported content remain for review.

Cooldowns, configured quiet hours, conversation/day caps, contact/global limits, and suspension after repeated failures bound unattended interaction. Limits count attempts conservatively, including uncertain outcomes. Identity-based history protects against resetting limits by deleting and recreating a local contact. Global pause and contact stop latches are checked again at the platform boundary to cover asynchronous settings updates.

These are conservative deterministic checks, not comprehensive semantic safety or proof of factual correctness. Unknown content is not granted automatic sending just because a model returned text.

## Optional cloud generation

The owner must select OpenAI, provide their own key, and explicitly enable cloud-sharing consent. Selecting the provider alone does not authorize a request. Optional limited context is a separate choice. The integration transmits the selected incoming notification, configured writing preferences and selected approved examples/contact instruction, plus at most six partial context turns if enabled. It does not transmit an entire inbox or secretly import conversations.

The endpoint is fixed HTTPS Responses API. Normal TLS verification remains enabled; the API key is an authorization header rather than prompt content. `store: false` is requested, but this does not promise zero provider retention or remove provider abuse-monitoring policies. Review [AI.md](AI.md) and current OpenAI data controls before consent. A ChatGPT subscription is not an API credential. API billing is the owner's responsibility; no free/cloud/local equivalence is implied.

Credential-like messages and selected request fields are blocked before cloud transport using conservative patterns. Draft validation rejects possible secrets and unsupported content. Provider errors are sanitized; no response bodies, key values, raw exception payloads, or private message text are written into diagnostics. Connection/read/overall timeouts, cancellation, a bounded worker queue, and a session cloud-attempt cap limit activity. No automatic API retry occurs. Failure may produce an explicitly review-only offline template.

Provider-reported input/output token usage may appear in activity. Cost remains unknown unless verified pricing is supplied; the app does not invent a price. Use provider account usage controls as well. Live provider access, account billing, and generation quality are separate from fake-transport tests and require an owner-authorized test.

## Exports

**Sanitized diagnostics** use an explicit allowlist: app/device versions, configuration flags, provider/model/token limits, capability flags, stage counts, and relevant timestamps. They exclude sender/contact labels, message/draft/style/example text, raw keys, identifiers/fingerprints, API keys, intents, private errors, and arbitrary activity details. Configuration flags show eligibility, not proof that a request or send occurred.

**Settings export** includes appearance and writing preferences, including the examples the owner explicitly supplied. It excludes keys, contacts, conversations, dispatch history, cloud-sharing authorization, and automation authorization. The owner chooses the destination through Android's document picker. An exported settings file is not an encrypted backup and may contain personal writing examples; store it accordingly. The exported file contains no authorization that could silently re-enable automation or cloud sharing.

General logcat, Android bugreports, repository issues, and third-party backup tools are outside these sanitized export controls. Reproduce problems with synthetic messages and a consenting test contact instead of uploading real conversations or unrestricted device logs.

## Signing and reporting

Private distribution still requires verification, predictable signing, and device setup. Keep the dedicated release key and its recovery material outside the repository and preserve it for updates. Debug APKs and clean-runner debug signatures are evaluation artifacts, not a stable production signing strategy. Changing keys usually forces uninstall and loses local data.

When reporting an issue, include VEIL version, Android/One UI and source-app versions, harmless reproduction steps, expected/actual stage, and a reviewed diagnostic report. Do not include OTPs, account/API credentials, signing secrets, real conversation text, or unrestricted bugreports.
