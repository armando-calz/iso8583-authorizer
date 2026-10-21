package io.github.armandocalz.authorizer.emv;

import java.io.ByteArrayOutputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * EMV data from field 55: the cryptogram and the data elements the card used to compute it.
 */
public final class IccData {

    public static final int APPLICATION_CRYPTOGRAM = 0x9F26;
    public static final int ATC = 0x9F36;
    public static final int PAN_SEQUENCE_NUMBER = 0x5F34;
    public static final int ISSUER_AUTHENTICATION_DATA = 0x91;

    /**
     * The data the cryptogram covers, in order: EMV Book 2's recommended minimum set (amount
     * authorised, amount other, terminal country code, TVR, transaction currency code, transaction
     * date, transaction type, unpredictable number, AIP, ATC), with their lengths in bytes.
     */
    static final List<int[]> CRYPTOGRAM_INPUT = List.of(
            new int[] {0x9F02, 6}, new int[] {0x9F03, 6}, new int[] {0x9F1A, 2}, new int[] {0x95, 5},
            new int[] {0x5F2A, 2}, new int[] {0x9A, 3}, new int[] {0x9C, 1}, new int[] {0x9F37, 4},
            new int[] {0x82, 2}, new int[] {ATC, 2});

    private final Map<Integer, byte[]> objects;

    private IccData(Map<Integer, byte[]> objects) {
        this.objects = objects;
    }

    /**
     * @throws IllegalArgumentException if the TLV is malformed or a required element is missing
     *         or has the wrong length
     */
    public static IccData parse(String hex) {
        IccData data = new IccData(Tlv.parse(HexFormat.of().parseHex(hex)));
        data.require(APPLICATION_CRYPTOGRAM, 8);
        CRYPTOGRAM_INPUT.forEach(element -> data.require(element[0], element[1]));
        return data;
    }

    public byte[] cryptogram() {
        return objects.get(APPLICATION_CRYPTOGRAM).clone();
    }

    public int atc() {
        byte[] atc = objects.get(ATC);
        return ((atc[0] & 0xFF) << 8) | (atc[1] & 0xFF);
    }

    /** Amount authorised (tag 9F02, n12 BCD) in minor units: the amount the card signed. */
    public long amountAuthorisedMinor() {
        return Long.parseLong(HexFormat.of().formatHex(objects.get(0x9F02)));
    }

    /** Transaction currency code (tag 5F2A, n3 BCD), e.g. "484": the currency the card signed. */
    public String currencyCode() {
        return HexFormat.of().formatHex(objects.get(0x5F2A)).substring(1);
    }

    /** Two digits; "00" when the card does not send one. */
    public String panSequenceNumber() {
        byte[] psn = objects.get(PAN_SEQUENCE_NUMBER);
        return psn == null ? "00" : HexFormat.of().formatHex(psn);
    }

    /** The concatenated values the cryptogram is computed over. */
    byte[] cryptogramInput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CRYPTOGRAM_INPUT.forEach(element -> out.writeBytes(objects.get(element[0])));
        return out.toByteArray();
    }

    private void require(int tag, int length) {
        byte[] value = objects.get(tag);
        if (value == null || value.length != length) {
            throw new IllegalArgumentException("ICC data element %X missing or not %d bytes".formatted(tag, length));
        }
    }
}
