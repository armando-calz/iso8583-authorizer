package io.github.armandocalz.authorizer.iso8583;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ICC_DATA;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.NETWORK_MANAGEMENT_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ORIGINAL_DATA_ELEMENTS;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PROCESSING_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TERMINAL_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Iso8583CodecTest {

    private static final String TEST_PAN = "4111111111111111";

    private final Iso8583Codec codec = new Iso8583Codec();

    private static String ascii(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static byte[] bytes(String ascii) {
        return ascii.getBytes(StandardCharsets.US_ASCII);
    }

    private static IsoMessage authorizationRequest() {
        return IsoMessage.builder("0100")
                .set(PAN, TEST_PAN)
                .set(PROCESSING_CODE, "000000")
                .set(AMOUNT, "000000025000")
                .set(TRANSMISSION_DATE_TIME, "1014153000")
                .set(STAN, "000123")
                .build();
    }

    @Nested
    class Encoding {

        @Test
        void encodesMtiBitmapAndFieldsInOrder() {
            // Fields 2, 3, 4, 7, 11 -> bits 2,3,4,7 in byte 1 (0x72) and bit 11 in byte 2 (0x20)
            assertThat(ascii(codec.encode(authorizationRequest()))).isEqualTo(
                    "0100" + "7220000000000000" + "16" + TEST_PAN + "000000" + "000000025000" + "1014153000" + "000123");
        }

        @Test
        void addsASecondaryBitmapOnlyWhenAFieldAbove64IsPresent() {
            IsoMessage reversal = IsoMessage.builder("0400")
                    .set(STAN, "000124")
                    .set(ORIGINAL_DATA_ELEMENTS, "0100" + "000123" + "1014153000" + "0".repeat(22))
                    .build();

            String encoded = ascii(codec.encode(reversal));

            // Primary: bit 1 (secondary present) + bit 11 -> 80 20 ...; secondary: bit 90 = bit 26 -> 00 00 00 40 ...
            assertThat(encoded).startsWith("0400" + "8020000000000000" + "0000004000000000" + "000124");
            assertThat(ascii(codec.encode(authorizationRequest()))).doesNotContain("8020");
        }

        @Test
        void prefixesBinaryFieldsWithTheirLengthInBytes() {
            IsoMessage message = IsoMessage.builder("0100").set(ICC_DATA, "9F2701809F3602000A").build();

            assertThat(ascii(codec.encode(message))).endsWith("009" + "9F2701809F3602000A");
        }

        @Test
        void encodesAMessageWithoutDataElements() {
            assertThat(ascii(codec.encode(IsoMessage.builder("0800").build()))).isEqualTo("0800" + "0".repeat(16));
        }
    }

    @Nested
    class Decoding {

        @Test
        void roundTripsAFullMessage() {
            IsoMessage message = authorizationRequest().toBuilder("0100")
                    .set(TERMINAL_ID, "TERM0001")
                    .set(ICC_DATA, "9F2608A1B2C3D4E5F60718")
                    .set(NETWORK_MANAGEMENT_CODE, "301")
                    .build();

            assertThat(codec.decode(codec.encode(message))).isEqualTo(message);
        }

        @Test
        void roundTripsRandomValidMessages() {
            Random random = new Random(8583);
            for (int i = 0; i < 1_000; i++) {
                IsoMessage message = RandomMessages.next(random);
                assertThat(codec.decode(codec.encode(message))).as("message %d", i).isEqualTo(message);
            }
        }

        @Test
        void rejectsTruncatedMessages() {
            String full = ascii(codec.encode(authorizationRequest()));

            for (int cut = 1; cut < full.length(); cut++) {
                byte[] truncated = bytes(full.substring(0, cut));
                assertThatThrownBy(() -> codec.decode(truncated)).as("cut at %d", cut).isInstanceOf(IsoFormatException.class);
            }
        }

        @Test
        void rejectsTrailingBytes() {
            byte[] withExtra = bytes(ascii(codec.encode(authorizationRequest())) + "X");

            assertThatThrownBy(() -> codec.decode(withExtra)).hasMessageContaining("trailing");
        }

        @Test
        void rejectsFieldsThisHostDoesNotSupport() {
            // Bit 5 (amount, settlement) is not in the field table
            assertThatThrownBy(() -> codec.decode(bytes("0100" + "0800000000000000" + "000000000100")))
                    .hasMessageContaining("Field 5");
        }

        @Test
        void rejectsInvalidBitmapsAndLengthPrefixes() {
            assertThatThrownBy(() -> codec.decode(bytes("0100" + "72200000000000ZZ")))
                    .hasMessageContaining("Invalid primary bitmap");
            assertThatThrownBy(() -> codec.decode(bytes("0100" + "4000000000000000" + "1A" + TEST_PAN)))
                    .hasMessageContaining("length prefix");
        }

        @Test
        void validatesDecodedValues() {
            // Field 3 must be numeric
            assertThatThrownBy(() -> codec.decode(bytes("0100" + "2000000000000000" + "00A000")))
                    .hasMessageContaining("Field 3");
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest
        @ValueSource(strings = {"01", "01000", "01A0", ""})
        void rejectsMalformedMtis(String mti) {
            assertThatThrownBy(() -> IsoMessage.builder(mti)).isInstanceOf(IsoFormatException.class);
        }

        @Test
        void rejectsValuesOfTheWrongLengthOrCharacterSet() {
            IsoMessage.Builder builder = IsoMessage.builder("0100");

            assertThatThrownBy(() -> builder.set(AMOUNT, "25000")).hasMessageContaining("expected exactly 12");
            assertThatThrownBy(() -> builder.set(AMOUNT, "00000002500O")).hasMessageContaining("not valid NUMERIC");
            assertThatThrownBy(() -> builder.set(PAN, "4".repeat(20))).hasMessageContaining("1..19");
            assertThatThrownBy(() -> builder.set(ICC_DATA, "9F2")).hasMessageContaining("not valid BINARY");
            assertThatThrownBy(() -> builder.set(TERMINAL_ID, "TERMÑ0001")).hasMessageContaining("Field 41");
        }

        @Test
        void rejectsFieldOneAndUnsupportedFields() {
            IsoMessage.Builder builder = IsoMessage.builder("0100");

            assertThatThrownBy(() -> builder.set(1, "0000000000000000")).hasMessageContaining("derived by the codec");
            assertThatThrownBy(() -> builder.set(5, "000000000100")).hasMessageContaining("not supported");
        }

        @Test
        void messagesAreImmutable() {
            IsoMessage message = authorizationRequest();

            assertThatThrownBy(() -> message.fields().put(STAN, "999999")).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
