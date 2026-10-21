package io.github.armandocalz.authorizer.emv;

import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plays the chip: builds the field 55 data a card would send with an authorization, including
 * the ARQC computed under the card's keys. For tests and the terminal simulator; uses test keys only.
 */
public final class EmvCardSimulator {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private final EmvCryptography cryptography;

    public EmvCardSimulator(EmvCryptography cryptography) {
        this.cryptography = cryptography;
    }

    /**
     * @param currency ISO 4217 numeric code, e.g. "484"
     * @param unpredictableNumber 4 bytes chosen by the terminal
     */
    public String iccData(String pan, String panSequenceNumber, int atc, long amountMinor, String currency,
            LocalDate date, byte[] unpredictableNumber) {
        Map<Integer, byte[]> tlv = new LinkedHashMap<>();
        tlv.put(IccData.APPLICATION_CRYPTOGRAM, new byte[8]); // computed below
        tlv.put(0x9F27, HEX.parseHex("80"));                  // cryptogram information data: ARQC
        tlv.put(0x9F02, HEX.parseHex("%012d".formatted(amountMinor)));
        tlv.put(0x9F03, HEX.parseHex("000000000000"));
        tlv.put(0x9F1A, HEX.parseHex("0" + currency));          // terminal country code (Mexico: 0484)
        tlv.put(0x95, HEX.parseHex("0000000000"));              // TVR: no terminal-detected issues
        tlv.put(0x5F2A, HEX.parseHex("0" + currency));
        tlv.put(0x9A, HEX.parseHex("%02d%02d%02d".formatted(date.getYear() % 100, date.getMonthValue(), date.getDayOfMonth())));
        tlv.put(0x9C, HEX.parseHex("00"));                      // transaction type: purchase
        tlv.put(0x9F37, unpredictableNumber.clone());
        tlv.put(0x82, HEX.parseHex("3900"));                    // AIP
        tlv.put(IccData.ATC, new byte[] {(byte) (atc >>> 8), (byte) atc});
        tlv.put(IccData.PAN_SEQUENCE_NUMBER, HEX.parseHex(panSequenceNumber));

        byte[] arqc = cryptography.arqc(pan, IccData.parse(HEX.formatHex(Tlv.encode(tlv))));
        tlv.put(IccData.APPLICATION_CRYPTOGRAM, arqc);
        return HEX.formatHex(Tlv.encode(tlv));
    }
}
