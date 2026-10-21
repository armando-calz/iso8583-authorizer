package io.github.armandocalz.authorizer.emv;

import java.util.HexFormat;

/** Key derivation from EMV 4.x Book 2, Annex A1. */
final class EmvKeys {

    private static final byte[] ALL_ONES = HexFormat.of().parseHex("FFFFFFFFFFFFFFFF");

    private EmvKeys() {
    }

    /**
     * ICC master key, Option A (A1.4.1): Y is the rightmost 16 digits of PAN || PAN sequence
     * number (left-padded with zeros if shorter); the key is 3DES(IMK)[Y] || 3DES(IMK)[Y xor FF..FF],
     * with odd parity.
     */
    static byte[] iccMasterKey(byte[] issuerMasterKey, String pan, String panSequenceNumber) {
        String digits = pan + panSequenceNumber;
        String y = digits.length() >= 16 ? digits.substring(digits.length() - 16) : "0".repeat(16 - digits.length()) + digits;
        byte[] block = HexFormat.of().parseHex(y);
        byte[] key = new byte[16];
        System.arraycopy(Des.tripleEncrypt(issuerMasterKey, block), 0, key, 0, 8);
        System.arraycopy(Des.tripleEncrypt(issuerMasterKey, Des.xor(block, ALL_ONES)), 0, key, 8, 8);
        return Des.withOddParity(key);
    }

    /**
     * Common Session Key derivation (A1.3.1) for application cryptograms: with
     * R = ATC || 00 00 00 00 00 00, the session key is 3DES(MK)[R with byte 3 = F0] ||
     * 3DES(MK)[R with byte 3 = 0F].
     */
    static byte[] sessionKey(byte[] iccMasterKey, int atc) {
        byte[] left = {(byte) (atc >>> 8), (byte) atc, (byte) 0xF0, 0, 0, 0, 0, 0};
        byte[] right = {(byte) (atc >>> 8), (byte) atc, (byte) 0x0F, 0, 0, 0, 0, 0};
        byte[] key = new byte[16];
        System.arraycopy(Des.tripleEncrypt(iccMasterKey, left), 0, key, 0, 8);
        System.arraycopy(Des.tripleEncrypt(iccMasterKey, right), 0, key, 8, 8);
        return key;
    }
}
