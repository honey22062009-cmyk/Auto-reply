# VEIL platform research — Phase 1

Research date: 2026-10-29. This document separates documented Android capabilities from capabilities that still require testing on the owner's Samsung Galaxy Tab A9+. Documentation is evidence of an API contract, not proof that a particular WhatsApp or Instagram release exposes it.

## Implementation boundary

Phase 1 is an offline compatibility prototype. It observes explicitly enabled supported apps, inspects notification contents and reply actions, and offers a controlled, user-initiated test. It does not enable automatic sending, connect an AI account, scrape a conversation, or claim delivery confirmation. Its deterministic test reply is a pipeline test, not a language model.

## Android notification access

- A listener is declared with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the `android.service.notification.NotificationListenerService` intent action. The system binds the service after the user grants notification access; this is separate from permission to post VEIL's own notifications.
- Wait for `onListenerConnected()` before querying active notifications. On disconnection, discard live reply handles; `requestRebind(ComponentName)` is the safe recovery API. Permission revocation, process death, reboot, and app replacement can interrupt observation. A listener is not an uninterrupted history feed.
- Low-RAM devices running Android 10 or earlier cannot bind notification listeners. Work-profile notifications may be blocked by device policy. These constraints must be checked rather than inferred from a tablet product name.

Source: [NotificationListenerService reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

Android 15 can redact OTP-bearing notification content from untrusted notification listeners. Missing or hidden previews must remain unavailable; VEIL must not try to recover them through screenshots, private storage, or other apps. Source: [Android 15 privacy changes](https://developer.android.com/about/versions/15/behavior-changes-all#sensitive-notifications).

## Message extraction and identity

`MessagingStyle` exposes only the messages placed in a particular notification. It is not complete conversation history. `Person` may provide a display name, URI, or key, but availability and stability depend on the publisher. A null message sender can represent a local-user message. Conversation titles and group flags must be interpreted separately from individual sender names. Reposts can include older messages. Source: [MessagingStyle](https://developer.android.com/reference/android/app/Notification.MessagingStyle) and [MessagingStyle.Message](https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message).

Engineering choices for VEIL:

- Restrict packages to `com.whatsapp` and `com.instagram.android`; exclude Business, cloned, work-profile, and unrelated applications unless explicitly implemented later.
- Treat group summaries as diagnostic observations, not replyable conversations.
- Prefer structured messaging metadata; fall back conservatively to title/text for inspection.
- A visible name is an observation, not a verified identity. Do not auto-enroll priority contacts or resolve name collisions by message frequency.
- Keep deduplication independent of display labels; version/fingerprint each observed notification snapshot and invalidate a reviewed reply when the notification changes.
- Recheck the live notification before dispatch. A stored database row is not authority to reuse a previous `PendingIntent`.

## Reply action inspection and dispatch

`RemoteInput` carries user-supplied text using the action's declared result key. Attach results with `RemoteInput.addResultsToIntent`; never guess an app's internal extra names. Free-form text input is different from data-only or predefined-choice input. Source: [RemoteInput](https://developer.android.com/reference/android/app/RemoteInput).

An action's semantic identifier distinguishes replies from marking as read, deleting, or other operations. The API also exposes whether device authentication is required. Phase 1 should reject ambiguous or non-reply operations and require an unlocked device for controlled tests. Source: [Notification.Action](https://developer.android.com/reference/android/app/Notification.Action).

Direct reply depends on a suitably mutable action provided by the originating application. Its `PendingIntent` can be cancelled or become stale; successful `send()` means Android accepted the dispatch, not that a server or recipient received the text. VEIL cannot repair another app's immutable or absent reply action. Source: [PendingIntent](https://developer.android.com/reference/android/app/PendingIntent).

Android 14+ restricts background activity starts using pending intents; Android 15+ changes the default creator privileges. These restrictions do not create a missing reply API. Keep tests in a visible activity; do not grant broad background-activity privileges to unrelated targets or try to bypass restrictions. Prefer an ordinary user-initiated launch of the source app for fallback. Sources: [Android 14 targeted changes](https://developer.android.com/about/versions/14/behavior-changes-14#background-activity-starts), [Android 15 targeted changes](https://developer.android.com/about/versions/15/behavior-changes-15#background-activity-starts).

Four separate observations must remain separate:

| Observation | What it proves | What it does not prove |
| --- | --- | --- |
| Notification detected | Android exposed a supported-app notification | Preview, identity, or reply availability |
| Reply action detected | An acceptable action was present in that snapshot | It will still be present or usable later |
| Android accepted dispatch | `PendingIntent.send()` did not report immediate failure | Network send, delivery, reading, or correct recipient |
| Recipient reports receipt | A consenting human independently checked the message | An automatic delivery receipt API or future compatibility |

## WhatsApp and Instagram

No universal notification format or direct-reply contract for either installed personal app was established by this research. Test the exact device, Android/One UI versions, account configuration, and app versions. Notification settings, foreground usage, grouping, message requests, and app updates may change available information. Until tested, both platforms' reply capabilities are **unverified**.

The WhatsApp Cloud API belongs to the WhatsApp Business Platform. It is not permission to access arbitrary personal-account messages. Phase 1 does not use that API. Source: [Meta's official WhatsApp Cloud API collection](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api).

Meta's Instagram messaging integration is for professional accounts and requires account authorization and messaging permissions; the Instagram Login flow documents `instagram_business_manage_messages`. This does not establish access to the owner's ordinary personal DMs. Phase 1 does not use it. Source: [Meta's official Instagram API collection](https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api).

The direct Meta developer documentation endpoints returned HTTP 429 during this research. The linked Meta-owned Postman collections supplied the above API-scope evidence; no account integration was performed. If Instagram exposes no usable reply action, copying the test text and opening Instagram is the supported fallback. Opening an app does not select or verify a recipient.

## ChatGPT and future engines

The [official ChatGPT Android FAQ](https://help.openai.com/en/articles/8142208-chatgpt-ios-and-android-app-faq) does not document an Android background-generation service/IPC contract for third-party messaging assistants. No such installed-app integration was verified. Do not read ChatGPT tokens, automate its UI, or reuse its private app data.

API billing is separately managed from ChatGPT subscriptions. Never assume a subscription supplies an API key or paid API balance. Source: [OpenAI billing systems](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform).

Current official documentation also describes separately authorized **Sign in with ChatGPT** plan usage for eligible tools and selected private apps. That is a cloud OAuth/API integration with eligibility, registration, scopes, user consent, and usage limits; it is not access to the installed Android app or its conversation history. It needs a separate feasibility/security review before implementation in VEIL. Phase 1 does not connect it. Sources: [Sign in with ChatGPT quickstart](https://developers.openai.com/siwc/quickstart), [OpenAI integration article](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt).

A future `OpenAIReplyEngine` should use explicit content-sharing consent, user-provided credentials protected by Android Keystore-backed encryption, bounded timeouts, cancellation, rate-limit handling, model selection, and usage accounting. These are later-phase requirements, not Phase 1 features.

## On-device models: evaluation only

Google's Android LLM Inference guide now marks MediaPipe LLM Inference as maintenance-only and directs new work toward LiteRT-LM. It describes fully on-device inference, but also warns that the MediaPipe path is optimized for high-end phones and does not reliably support emulators. The guide includes a quantized Gemma 3 1B example. These facts do not establish acceptable Tab A9+ speed or quality. Source: [Google AI Edge Android guide](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android).

Before choosing a model, record actual device model/SoC, Android version, RAM and available memory, supported ABIs, free storage, backend support, model license, exact quantization and download size. Measure time to first token, generation latency, peak memory, thermal behavior, and battery cost with a small approved English/Hindi/Hinglish test set. Assess unknown-fact handling and casual-language quality. A compact quantized model is an evaluation candidate; no model is bundled, downloaded, benchmarked, or claimed to work in this milestone.

## Samsung lifecycle and sideloading

Samsung provides Sleeping/Deep sleeping/Never sleeping controls under Battery background usage limits; names vary by One UI version. Changing these may affect battery consumption and does not guarantee service uptime. Investigate only if the device tests show missed callbacks. Sources: [Samsung battery support](https://www.samsung.com/us/support/answer/ANS10002534/), [Samsung application management](https://developer.samsung.com/mobile/app-management.html).

Sideloaded apps can encounter Android's restricted-settings protection. The owner may need to review the app under system App info and allow restricted settings before granting notification access. Do not bypass this review or imply notification access is harmless. Source: [Android restricted settings help](https://support.google.com/android/answer/12623953).

## Glass rendering

Compose's `Modifier.blur` blurs a composable's own rendered layer; it does not sample arbitrary content behind it. Supported uniform blur requires Android 12+. A layered translucent surface with highlights is a designed glass approximation unless a real backdrop capture/sampling path is implemented and verified. Keep text crisp, provide an opaque/low-effect fallback, and measure GPU cost on the tablet. Sources: [Compose blur overview](https://developer.android.com/develop/ui/compose/graphics/blur/overview), [Compose Modifier reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier).
