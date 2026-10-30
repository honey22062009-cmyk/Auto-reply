# VEIL 1.0.0 release verification

Version 1 source implements optional cloud suggestions, offline templates, reviewed sending, conservative opt-in priority automation, stable identity binding, persistent duplicate/rate protection, writing preferences, five accent palettes, and Mote guidance.

The first integrated host run completed 202 tests without failures. Release lint then identified a minimum-API mismatch in notification identity extraction; this is fixed and an Android 9 regression test added. Final verification and signed APK details will be recorded after execution completes. No newer test result is implied by this in-progress record.

The Samsung tablet is not attached, and no real messaging account or paid OpenAI API call has been used. WhatsApp/Instagram behavior, recipient receipt, and device battery/performance remain subject to the compatibility guide. Mock transport tests verify request and response handling; they do not establish live provider access for a future owner's key/model.

The new release signing key stays outside Git. Installation over the earlier differently signed prototype may require a fresh install; see [RELEASE_SIGNING.md](RELEASE_SIGNING.md). Never upload the key or its password to this public repository.
