# VEIL Version 1 feature guide

This describes implemented behavior. Executed build/test results belong in [BUILD_VERIFICATION.md](BUILD_VERIFICATION.md); this guide does not claim that a particular source revision, provider account, or physical device passed.

## Everyday use

**Home** shows notification access, observation/automation state, enabled platforms, retained activity, and next-step links. **Inbox** is a notification-derived activity view. It can generate a draft, let you edit/copy it, open the source app, dismiss an observation, or request an exact reviewed send when a supported action is still active. It is not the complete WhatsApp/Instagram conversation history.

**Priority** stores explicit local labels and contact rules. Contacts do not become priority automatically from message frequency. **Activity** separates observation, generation/review, dispatch attempts, Android acceptance, failures, and user-reported receipt. **Settings** manages appearance, capture/automation controls, AI Studio, companion visibility, retention, deletion, and exports. **Compatibility Lab** records only capabilities actually observed on this device and walks through a consenting-contact test.

No supported action means no automatic platform integration is invented. Copy a reviewed draft and open WhatsApp or Instagram to send it yourself. Personal-account Instagram APIs are not assumed to provide unrestricted DM access. Platform actions can expire or differ across app versions and privacy settings.

## Glass, colors, and Mote

Light, dark, and system themes combine with five accents: Rose, Coral, Plum, Amber, and Silver. Glass intensity changes the visual treatment. The floating capsule dock adapts to a navigation rail in sufficiently wide windows. Layouts account for system insets, split-screen sizing, and large text.

Glass is a supported-effects approximation: layered translucency, restrained light, edges, and decorative blur where supported. It does not claim operating-system backdrop sampling or allow decorative effects to replace readable surfaces.

Mote is an optional, interactive in-app companion. Tapping it reveals practical guidance and navigation actions based on local app state. It never sends a message, signs in, or answers as a cloud chatbot. You can hide it and enable reduced motion. Mote's animation is restrained and its guidance is deterministic.

## AI Studio and writing style

The default offline templates offer short English/Hindi/Hinglish suggestions without a model download or network. They recognize only limited patterns and are clearly distinct from an on-device LLM. Free-form personality, examples, and complex context have limited effect on these deterministic patterns.

The optional OpenAI Responses engine needs your own encrypted API key and explicit cloud-sharing consent. You can configure its model, output-token cap, and separate context opt-in. Selected message text and writing preferences/examples leave the device only for an authorized cloud request. `store: false` does not mean zero provider retention. Provider billing is separate from ChatGPT subscriptions, and no price or free entitlement is invented.

Writing preferences cover language, warmth, humor, emoji use, length, preferred/avoided phrases, and examples you explicitly supply. Preview uses the selected supported engine, and unknown facts are not treated as permission to invent an answer. No secret conversation scraping or complete-personality learning is implied. Provider failures can yield an offline fallback that still requires review.

Context is limited to at most six selected partial notification-derived turns for the same exposed stable identity, only when enabled. A notification listener does not provide full conversation history or reliable knowledge of everything sent on other devices.

## WhatsApp and Instagram contact details (v1.1)

Priority now keeps the two platforms visually separate. The WhatsApp form accepts the tablet's saved contact name, optional phone number with country code, and the exact name shown in notifications if different. The Instagram form accepts a display name, username, optional numeric user ID, and notification sender override.

These user-typed account details are encrypted **reference hints**, not proof of account identity. NotificationListenerService may never see phone numbers or numeric Instagram IDs. VEIL does not look them up or match them by guessing. After the user selects a retained notification from the person, the repository checks that its sender matches the saved notification name, that the notification carries stable app-provided identity metadata, and that a separately reported recipient receipt confirms the controlled test. The actual bound notification identity is what the safe automation pipeline compares.

Changing sender name, username, numeric ID, phone number, or platform disables any previously approved automatic rule and clears identity binding. The per-contact pause remains available. Advanced cooldown and quiet-hours rules are tucked away by default. Instagram replies cannot be promised without an actual compatible `RemoteInput` action from the installed Instagram version.

## Enabling restricted automation

1. Enable the messaging platform and Android notification access.
2. Ask a consenting one-to-one contact to send a harmless fresh test message.
3. Inspect the notification in Inbox/Lab. Review the fixed compatibility text, confirm consent, and attempt a test only if Android exposes a supported live action.
4. Ask the contact to check receipt and record their report separately. Android acceptance alone is not receipt confirmation.
5. Bind the tested notification's exposed stable identity to the chosen local priority contact. A displayed name alone cannot be used for unattended identity matching.
6. Configure contact limits, quiet hours, pause state, and optional instruction. Explicitly enable both that contact and the global automation control.

Binding keeps automatic sending off until deliberately enabled. A normal manual-reply receipt cannot substitute for a fixed TEST result. If the source app does not expose stable identity or a usable action, suggestions/manual fallback remain available while automation is unavailable.

Even configured contacts receive unattended replies only for narrowly recognized self-contained greetings and conservative greeting drafts. Sensitive financial/security/medical/personal messages, threats, commitments, factual questions, unknown text, groups, stale/replayed notifications, missing context, and generator review warnings do not become automatic responses. Cooldowns, contact/global limits, conversation/day caps, quiet hours with the device timezone, and repeated-failure suspension remain in force. The global pause stops capture and blocks replies; individual contacts can be paused independently.

A persistent attempt is recorded before IPC. Failed, uncertain, interrupted, manual, automatic, and test attempts cannot be silently retried against the same message. Live notifications and actions are revalidated immediately before using `RemoteInput`. No app-private API, hidden screen automation, credentials, or source-app session token is used.

## Privacy and retention

Names, previews, drafts, contact instructions, writing examples, and optional API credentials use Keystore-backed field encryption. The Room database as a whole is not encrypted; app names, timestamps, state flags, opaque fingerprints/identity anchors, and dispatch metadata remain structured data. No central message server, advertising SDK, or analytics service is included.

Visible history defaults to one day, with one/three/seven-day controls. Purging occurs when the app processes or maintains data, not necessarily at an exact deadline while Android has stopped it. Deleting history clears visible messages, drafts, events, and live reply handles. A content-free 30-day dispatch ledger remains to prevent duplicate replies and preserve limits. Contact deletion does not reset that safety ledger.

Settings export excludes contacts, message history, ledger, API keys, cloud consent, and automation authorization. It includes supplied writing examples. Diagnostics omit private content/identifiers/secrets and describe capabilities as separate observed stages. Choose export destinations consciously; settings export is not an encrypted backup.

## Verification still required

A generic emulator can exercise app startup, Room migration, controls, UI, and Keystore behavior. It cannot establish Samsung One UI reliability, actual WhatsApp/Instagram notification layouts and actions, recipient receipt, long-run battery/thermal impact, or live OpenAI account access and response quality. Use the [tablet guide](DEVICE_TEST_GUIDE.md) and revision-specific verification results. No live provider request or paid generation is implied by source implementation or fake-transport tests.
