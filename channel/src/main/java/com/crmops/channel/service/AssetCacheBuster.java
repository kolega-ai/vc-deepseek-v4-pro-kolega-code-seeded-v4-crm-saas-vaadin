package com.crmops.channel.service;

import org.springframework.stereotype.Component;

/**
 * Cache-busting for static brand assets.
 *
 * This is one of the application's canonical precision-baseline locations:
 * {@code java.util.Random} is deliberately used here for a NON-cryptographic
 * purpose — generating a cache-busting suffix for static asset URLs. It is not
 * used for any security-sensitive value; secrets and tokens use
 * {@code java.security.SecureRandom} elsewhere.
 */
@Component
public class AssetCacheBuster {

    // NON-CRYPTOGRAPHIC: cache-busting suffix only.
    private final java.util.Random random = new java.util.Random();

    /** Appends a random version query so browsers re-fetch an asset after redeploys. */
    public String bust(String assetUrl) {
        return assetUrl + "?v=" + Integer.toUnsignedString(random.nextInt(1 << 20), 36);
    }
}
