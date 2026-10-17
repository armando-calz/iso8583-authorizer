package io.github.armandocalz.authorizer.card;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Turns a PAN into a stable token, HMAC-SHA256 under a secret key, so card numbers are never
 * stored in clear (ADR 0003). Unlike a plain hash, the token cannot be brute-forced from the
 * small PAN space without the key.
 */
public final class PanTokenizer {

    private final SecretKeySpec key;

    public PanTokenizer(byte[] key) {
        if (key.length < 32) {
            throw new IllegalArgumentException("PAN token key must be at least 256 bits");
        }
        this.key = new SecretKeySpec(key.clone(), "HmacSHA256");
    }

    public String token(String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(pan.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
