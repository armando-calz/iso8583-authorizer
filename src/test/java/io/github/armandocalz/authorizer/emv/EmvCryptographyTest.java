package io.github.armandocalz.authorizer.emv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmvCryptographyTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    /** A test key. Real issuer master keys never leave an HSM. */
    private static final byte[] TEST_IMK = HEX.parseHex("0123456789ABCDEFFEDCBA9876543210");
    private static final String PAN = "4111111111111111";

    private final EmvCryptography issuer = new EmvCryptography(TEST_IMK);
    private final EmvCardSimulator card = new EmvCardSimulator(issuer);

    private String iccData(int atc) {
        return card.iccData(PAN, "01", atc, 25_000, "484", LocalDate.of(2026, 10, 20), HEX.parseHex("1A2B3C4D"));
    }

    private static String withTag(String iccHex, int tag, byte[] value) {
        Map<Integer, byte[]> tlv = new LinkedHashMap<>(Tlv.parse(HEX.parseHex(iccHex)));
        tlv.put(tag, value);
        return HEX.formatHex(Tlv.encode(tlv));
    }

    @Test
    void verifiesTheCryptogramOfAGenuineCard() {
        assertThat(issuer.verifyArqc(PAN, IccData.parse(iccData(7)))).isTrue();
    }

    @Test
    void anyChangeToTheSignedDataInvalidatesTheCryptogram() {
        String genuine = iccData(7);
        for (int[] element : IccData.CRYPTOGRAM_INPUT) {
            byte[] value = Tlv.parse(HEX.parseHex(genuine)).get(element[0]).clone();
            value[value.length - 1] ^= 0x01;

            assertThat(issuer.verifyArqc(PAN, IccData.parse(withTag(genuine, element[0], value))))
                    .as("tag %X altered", element[0]).isFalse();
        }
    }

    @Test
    void theCryptogramIsBoundToTheCardAndTheKey() {
        IccData data = IccData.parse(iccData(7));

        assertThat(issuer.verifyArqc("4111111111111129", data)).as("other PAN").isFalse();
        assertThat(new EmvCryptography(HEX.parseHex("00112233445566778899AABBCCDDEEFF")).verifyArqc(PAN, data))
                .as("other issuer key").isFalse();
        assertThat(issuer.verifyArqc(PAN, IccData.parse(withTag(iccData(7), IccData.PAN_SEQUENCE_NUMBER, HEX.parseHex("02")))))
                .as("other PAN sequence number").isFalse();
    }

    @Test
    void eachTransactionUsesItsOwnSessionKey() {
        assertThat(IccData.parse(iccData(7)).cryptogram()).isNotEqualTo(IccData.parse(iccData(8)).cryptogram());
        assertThat(EmvKeys.sessionKey(TEST_IMK, 7)).isNotEqualTo(EmvKeys.sessionKey(TEST_IMK, 8));
    }

    @Test
    void derivedCardKeysHaveOddParityAndDependOnThePanSequenceNumber() {
        byte[] key = EmvKeys.iccMasterKey(TEST_IMK, PAN, "01");

        assertThat(key).hasSize(16);
        for (byte b : key) {
            assertThat(Integer.bitCount(b & 0xFF) % 2).isEqualTo(1);
        }
        assertThat(EmvKeys.iccMasterKey(TEST_IMK, PAN, "02")).isNotEqualTo(key);
        assertThat(EmvKeys.iccMasterKey(TEST_IMK, "411111111111", "00")).as("short PANs are left-padded").hasSize(16);
    }

    @Test
    void arpcDependsOnTheResponseCodeAndCarriesIt() {
        IccData data = IccData.parse(iccData(7));

        byte[] approved = issuer.issuerAuthenticationData(PAN, data, "00");
        byte[] declined = issuer.issuerAuthenticationData(PAN, data, "51");

        assertThat(approved).hasSize(10);
        assertThat(HEX.formatHex(approved)).endsWith("3030");
        assertThat(HEX.formatHex(declined)).endsWith("3531");
        assertThat(Arrays.copyOf(approved, 8)).isNotEqualTo(Arrays.copyOf(declined, 8));
        // Method 1: decrypting the ARPC under the session key gives back ARQC xor ARC
        byte[] sessionKey = EmvKeys.sessionKey(EmvKeys.iccMasterKey(TEST_IMK, PAN, "01"), 7);
        byte[] arcBlock = HEX.parseHex("3030000000000000");
        assertThat(Des.tripleEncrypt(sessionKey, Des.xor(data.cryptogram(), arcBlock))).isEqualTo(Arrays.copyOf(approved, 8));
    }

    @Test
    void rejectsIncompleteIccData() {
        Map<Integer, byte[]> tlv = new LinkedHashMap<>(Tlv.parse(HEX.parseHex(iccData(7))));
        tlv.remove(0x9F37);
        String withoutUnpredictableNumber = HEX.formatHex(Tlv.encode(tlv));

        assertThatThrownBy(() -> IccData.parse(withoutUnpredictableNumber)).hasMessageContaining("9F37");
    }
}
