package com.altomedia.herbalindo

/**
 * Build-time constants. AdMob unit IDs and the Functions region are injected through
 * resources so release builds can swap them without touching source.
 */
object Config {

    /** AdMob app ID, read from the manifest placeholder defined in build.gradle.kts. */
    const val ADMOB_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"
    const val ADMOB_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

    const val FUNCTIONS_REGION = "asia-southeast2"

    /** Support contact shown on the profile and legal screens. */
    const val SUPPORT_EMAIL = "altomediaindonesia@gmail.com"
    const val DEVELOPER = "ALTOMEDIA"

    const val PRIVACY_URL =
        "https://kdsmedia.github.io/HERBALINDO/privacy-policy.html"
    const val TERMS_URL =
        "https://kdsmedia.github.io/HERBALINDO/terms-of-service.html"

    /** Ad load retry backoff, kept short so a weak connection does not stall the UI. */
    const val AD_RETRY_DELAY_MS = 5_000L
}