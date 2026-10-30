# VEIL Phase 1 — Samsung compatibility test

These are tests for the owner to run on the actual tablet. They have not been performed by the development environment. Run WhatsApp and Instagram separately and record failures as results. This milestone never sends automatically.

## Before testing

1. Install the built VEIL APK using the installation instructions in the project README. Confirm its package is `dev.veil.app`. It is independent of Strict Blocker.
2. Use only a consenting test contact who understands that the reply is a technical test. Do not use financial, medical, intimate, password, or OTP content. A suitable incoming message is `VEIL compatibility test 01`.
3. Record tablet model, RAM variant, Android release/API, One UI version, build/security patch, VEIL version, and each messaging app's version. Compatibility Lab shows available device/app details; One UI version may require Settings → About tablet → Software information.
4. Verify the official WhatsApp (`com.whatsapp`) and Instagram (`com.instagram.android`) apps are installed in the same personal profile as VEIL. Clones, Secure Folder, work profiles, and WhatsApp Business are outside this test's claims.
5. In VEIL onboarding, read the notification-access explanation and explicitly enable each platform to inspect. Open Android notification-access settings and grant VEIL access. Return to VEIL and check the listener connection status.
6. If access is blocked as a restricted setting after sideloading, review Settings → Apps → VEIL → menu → Allow restricted settings, if your Android version offers it. Grant only after reviewing and trusting this APK. Then revisit notification access. See [Android's official explanation](https://support.google.com/android/answer/12623953).
7. Check that the messaging app's notifications and previews are enabled for the consenting conversation. Keep the tablet unlocked for reply tests. Exit the source conversation so the app can post an incoming notification.

## The four-stage test

| Stage | Owner action | Record |
| --- | --- | --- |
| 1. Detection | Ask the consenting contact to send one harmless unique test message. Open VEIL's Compatibility Lab and Inbox. | Notification detected time; accessible preview yes/no; sender metadata yes/no; group status. |
| 2. Action inspection | Inspect the corresponding notification entry while it remains active. | Reply action available/unavailable and explanation. An unavailable action is a valid result. |
| 3. Dispatch | Only if available: select the controlled test reply, review the platform and recipient, confirm consent, and explicitly send once. | Attempted time; Android accepted dispatch, blocked/stale, or failed. Do not interpret accepted dispatch as delivery. |
| 4. Independent receipt check | Ask the consenting contact to check the correct conversation on their own device. | Their report of exact test text/recipient/time, or no receipt. Record a user-reported receipt in VEIL only after this check. |

If the action is missing or unavailable, copy the test text and open the source app. Verify the recipient yourself before pasting/sending. Mark this as **manual fallback**, not successful RemoteInput dispatch.

An app may require connectivity to deliver after accepting the Android dispatch. An incoming notification may disappear when the source conversation is opened. Never repeatedly press send to compensate for uncertain receipt. Ask the contact to confirm first.

## Behavior matrix

Use a fresh unique test message for each row. Do not run tests against non-consenting contacts.

| Scenario | Procedure | Expected VEIL behavior to check |
| --- | --- | --- |
| Baseline single message | Source app backgrounded, previews enabled. | One observation; clear capability status; no autonomous send. |
| Same notification updated | Contact sends two messages quickly. | Latest snapshot shown; earlier reviewed reply cannot silently target the changed state. |
| Repeated callback/repost | Leave notification active; return to VEIL and reconnect listener. | No extra dispatch and no duplicate suggestion caused solely by repost. |
| Two conversations | Two consenting contacts send distinct messages. | Distinct entries and action targets; no cross-conversation reply. |
| Same visible name | If practical, two consenting test identities share a displayed name. | No claim of verified identity and no automatic matching/sending. |
| Group conversation | Consenting test group sends a message. | Group identified when metadata permits; group summary never treated as a direct conversation. Controlled reply may be conservatively unavailable. |
| Hidden previews | Disable message previews and send a harmless test. | Hidden/missing information stays unavailable; no invented message/sender. |
| Source conversation foregrounded | Open that conversation, then receive a test. | No promise of detection when no notification is posted. |
| Notification removed | Dismiss the notification before sending in VEIL. | Reply action becomes unavailable; stale reply blocked. |
| User already replied | Reply in the source app or notification shade before returning to VEIL. | Do not claim complete knowledge of response history; removed/changed/local-user notifications invalidate a pending test. |
| Listener revoked | Revoke notification access in Android settings. | Status updates on returning to VEIL; dispatch blocked; no new observations. |
| Pause | Pause VEIL, then receive a harmless test. | No processing/dispatch until explicitly resumed; retained history remains clearly historical. |
| Platform disabled | Disable one platform in VEIL and receive a message there. | No new processing or reply from the disabled platform. |
| Reboot | Reboot, unlock, reopen VEIL, send a fresh test. | Listener status is honest; recovery or manual reconnect works; no replayed reply. |
| Process termination | With no dispatch in progress, close/terminate VEIL, reopen, then receive a fresh test. | Settings/history restore; stale in-memory handles are not trusted. |
| Force stop | Force stop VEIL in App info, then send a test. Reopen VEIL afterward. | Document expected interruption; explicit reopen/reconnect may be necessary. |
| App update | Install a same-signed VEIL update and then test. Repeat after a messaging-app update. | No automatic sending; recheck capabilities instead of carrying forward an unsupported guarantee. |
| Offline | After a notification arrives, disconnect networking and explicitly perform a consenting test once. | Android acceptance and remote receipt remain separate. Record delayed or absent receipt. |
| Lock screen | Lock the tablet after observing a message. Unlock before testing. | Authentication restrictions are respected; no bypass. |
| Instagram message request | Use a consenting account whose message goes to Requests. | Detect only what Instagram/Android expose; no promise of request access or reply support. |
| Unrelated notification | Trigger a non-message notification from a supported app. | No fabricated message or automatic reply. |

For uncertain group/identity observations, record the limitation and stop the test before sending.

## UI and accessibility pass

- Rotate portrait/landscape, resize split-screen, and change display scaling. The dock/rail and content should remain usable without clipped actions.
- Set Android font size to the largest setting. Check text wrapping, buttons, dialogs, Inbox, and diagnostics.
- Enable TalkBack. Check destination names, selected state, controls, status labels, and traversal order.
- Test light, dark, and system appearance; reduced-motion preferences; lower glass intensity; keyboard visibility during editing.
- Cancel each confirmation dialog; cancellation must send nothing. Double-tap the send action once during a consenting test; there must be at most one accepted dispatch.
- Export diagnostics and inspect the file before sharing. It should contain capability states and versions, not messages, sender labels, conversation identifiers, credentials, or raw notification extras.
- Delete local history and restart. Confirm the removed content no longer appears. Delete a priority label separately if one was configured.

## Background reliability and performance

Leave the tablet idle with the screen off for 30 minutes and then longer overnight; have a consenting contact send a scheduled harmless test manually. Record whether the source app posted a notification and whether VEIL observed it after unlock. Test both battery saver on and off. Do not infer success from a blank activity timeline.

If notifications are missed, inspect Battery → Background usage limits for VEIL and the messaging app. Samsung's sleeping/deep-sleeping settings can restrict background execution; a Never sleeping entry may help but can increase battery usage. Menu names vary. See [Samsung battery guidance](https://www.samsung.com/us/support/answer/ANS10002534/).

With optional ADB access, capture a cold launch and rendering statistics on the actual tablet:

```sh
adb shell am force-stop dev.veil.app
adb shell am start -W -n dev.veil.app/.MainActivity
adb shell dumpsys gfxinfo dev.veil.app reset
# Navigate the app on the tablet, then:
adb shell dumpsys gfxinfo dev.veil.app
adb shell dumpsys meminfo dev.veil.app
```

Do not distribute raw bugreports/logcat without reviewing them; Android diagnostics may include unrelated sensitive data. No 60 FPS, battery, thermal, or Tab A9+ latency claim is established by emulator or unit tests.

## Result record

Create one record per platform/version combination:

```text
Date/time/timezone:
VEIL version/build:
Device model / RAM:
Android API / One UI / security patch:
Platform package / version:
Notification access granted / listener connected:
Notification detected (time):
Preview available:
Sender metadata available (do not include sender's name in shared report):
Reply action available:
Dispatch attempted (time):
Android dispatch result:
Recipient receipt: unverified / independently checked by owner:
Recipient check time (if any):
Manual fallback used:
Scenarios tested / failures:
Sanitized diagnostic filename:
```

Leave unknown results unknown. A recipient's report is useful evidence but must be labeled user-reported, not an API delivery receipt. Repeat the baseline after app/OS updates before relying on future automation.
