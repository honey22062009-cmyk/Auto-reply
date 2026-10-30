# VEIL reply engines

Version 1 keeps reply generation separate from notification capture and dispatch. Engines produce drafts; neither engine has a sending API. Automation must independently apply VEIL's identity, consent, safety, freshness, rate, and pause rules before dispatch.

## Offline templates

`ManualTemplateEngine` is the default and uses no network. It offers English, Hindi, and Hinglish suggestions with a configurable emoji preference. Exact greetings, thanks, and goodbyes can produce conservative short responses; questions needing personal facts, acknowledgments, and sensitive content require review. Unknown messages do not receive invented availability, payments, promises, or shared experiences.

This is a deterministic template system, **not an on-device language model**. Free-form style, humor, examples, and context have limited effects with templates. No model download or claim of desktop-level local inference is included. A future native model would need actual tablet memory, license, language-quality, battery, and thermal testing.

## Optional OpenAI engine

`OpenAIReplyEngine` uses the official HTTPS Responses endpoint, `https://api.openai.com/v1/responses`. The owner must select OpenAI, supply their own API key, and explicitly allow selected message content to leave the device. The engine checks all three conditions before invoking transport. Key persistence belongs to the encrypted settings layer; no key is in source or the prompt. The installed ChatGPT application is not used as a background API or a credential source.

The configurable default is `gpt-5.4-mini`; OpenAI's model documentation lists Responses support and no reasoning by default. The older `gpt-4.1-mini` and `gpt-5-mini` documentation was marked deprecated when checked on 2026-10-30, so neither is the default. Model availability and account permissions must still be checked in the owner's OpenAI account. [GPT-5.4 Mini model documentation](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [model catalog](https://developers.openai.com/api/docs/models).

The request uses a fixed instruction block and one user-data JSON item. Incoming messages and partial notification context never become system/developer instructions or tool calls. All tools, remote conversation IDs, background mode, provider-side conversation history, and automatic retries are omitted. The parser uses the REST output-item format rather than assuming the SDK-only convenience property exists. [OpenAI text generation guide](https://developers.openai.com/api/docs/guides/text).

The request includes:

- Up to 4,096 characters of the selected incoming notification.
- Writing preferences, up to five explicitly approved examples, and optional owner contact preferences.
- At most six recent context turns of up to 1,200 characters each, **only when the separate context setting is enabled**. Context is always labeled partial.
- An output-token cap between 64 and 1,024, plus the owner's character-length preference.
- `store: false`.

The content is transmitted to OpenAI when cloud generation is requested or when an explicitly configured applicable rule requests it. This is not local processing or a promise of zero retention. OpenAI documents separate provider data controls; `store: false` does not itself remove all abuse-monitoring retention. Review the current provider policy before enabling cloud processing. [OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data).

## Failures, limits, and usage

Transport uses Android's normal TLS validation, a fixed provider URL, no redirects, a 15-second connect timeout, a 30-second read timeout, a 45-second overall request deadline, a bounded response body, and a bounded worker pool. Cancelling the coroutine disconnects the active request. Authentication, timeout, rate-limit, unavailable-provider, refused, incomplete, and malformed responses become sanitized failures; response bodies, keys, and raw exception text are not shown in those errors. Rate limits do not trigger automatic retries. [OpenAI API errors](https://developers.openai.com/api/docs/guides/error-codes).

Successful responses expose provider/model and reported input/output tokens. Estimated cost is intentionally unknown (`null`) rather than embedding a stale price. Provider billing and pricing can change and may include charges beyond simple text tokens; the owner should set provider-side usage limits and inspect their account. An existing ChatGPT subscription is not an API credential; VEIL does not obtain or assume access to subscription benefits, eligible credits, or OAuth entitlements.

Credential-related messages, selected context, examples, and style settings are blocked before cloud transmission using conservative patterns. Generated credential-like text, control/bidirectional spoof characters, blank drafts, and overlong drafts are rejected. Sensitive subjects, commitments, unverifiable personal questions, and disliked phrases produce review warnings. These checks are intentionally conservative heuristics and are **not proof that a message is safe or factually correct**. The independent automation rules remain mandatory; the owner can always edit or reject a draft.

The credential guard normalizes compatibility characters and removes format-only characters before matching, and treats standalone 4–8-digit sequences as possible verification codes. This may also exclude harmless years or numeric references; it is a deliberate privacy tradeoff. It is still a heuristic, not complete secret detection.

## Verification

The AI test suite uses synthetic provider responses and fake transport. It checks explicit cloud gates, credential blocking, context boundaries, untrusted-input separation, successful REST parsing and usage, redacted error handling, rate-limit behavior, cancellation propagation, refusal/incomplete rejection, and draft validation. It does not send a real API request or spend money. Live provider access, inference quality, account billing, and personalized Hinglish fluency are unverified until the owner performs an explicitly authorized test using their own provider account.
