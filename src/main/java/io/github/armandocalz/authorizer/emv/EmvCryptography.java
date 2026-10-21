package io.github.armandocalz.authorizer.emv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Issuer side of EMV application cryptograms (EMV 4.x Book 2), under one issuer master key for
 * application cryptograms (IMK-AC): ICC master key by Option A, session key by the Common Session
 * Key method, ARQC by ISO/IEC 9797-1 MAC Algorithm 3, ARPC by Method 1 (ADR 0006).
 */
public final class EmvCryptography {

    private final byte[] issuerMasterKey;

    public EmvCryptography(byte[] issuerMasterKey) {
        if (issuerMasterKey.length != 16) {
            throw new IllegalArgumentException("IMK-AC must be a 16-byte double-length key");
        }
        this.issuerMasterKey = issuerMasterKey.clone();
    }

    /** The ARQC a genuine card would compute for this data (the card's own computation). */
    public byte[] arqc(String pan, IccData data) {
        return Iso9797Mac.algorithm3(sessionKey(pan, data), data.cryptogramInput());
    }

    public boolean verifyArqc(String pan, IccData data) {
        return MessageDigest.isEqual(arqc(pan, data), data.cryptogram());
    }

    /**
     * ARPC Method 1: 3DES under the session key of ARQC xor (ARC || 00 00 00 00 00 00), where the
     * authorisation response code (ARC) is the ISO 8583 response code in ASCII.
     */
    public byte[] arpc(String pan, IccData data, String responseCode) {
        byte[] arc = Arrays.copyOf(responseCode.getBytes(StandardCharsets.US_ASCII), 8);
        return Des.tripleEncrypt(sessionKey(pan, data), Des.xor(data.cryptogram(), arc));
    }

    /** Value of tag 91 (issuer authentication data): ARPC (8 bytes) || ARC (2 bytes). */
    public byte[] issuerAuthenticationData(String pan, IccData data, String responseCode) {
        byte[] out = Arrays.copyOf(arpc(pan, data, responseCode), 10);
        System.arraycopy(responseCode.getBytes(StandardCharsets.US_ASCII), 0, out, 8, 2);
        return out;
    }

    private byte[] sessionKey(String pan, IccData data) {
        return EmvKeys.sessionKey(EmvKeys.iccMasterKey(issuerMasterKey, pan, data.panSequenceNumber()), data.atc());
    }
}
