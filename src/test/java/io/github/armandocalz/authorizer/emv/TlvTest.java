package io.github.armandocalz.authorizer.emv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TlvTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    @Test
    void parsesOneAndTwoByteTags() {
        Map<Integer, byte[]> tlv = Tlv.parse(HEX.parseHex("9F2608A1B2C3D4E5F607189505000000000082023900"));

        assertThat(tlv.keySet()).containsExactly(0x9F26, 0x95, 0x82);
        assertThat(HEX.formatHex(tlv.get(0x9F26))).isEqualTo("A1B2C3D4E5F60718");
        assertThat(HEX.formatHex(tlv.get(0x82))).isEqualTo("3900");
    }

    @Test
    void roundTripsIncludingLongFormLengths() {
        Map<Integer, byte[]> objects = new LinkedHashMap<>();
        objects.put(0x9F10, new byte[200]);
        objects.put(0x91, HEX.parseHex("0102030405060708" + "3030"));

        byte[] encoded = Tlv.encode(objects);

        assertThat(HEX.formatHex(encoded)).startsWith("9F1081C8");
        assertThat(Tlv.parse(encoded)).containsOnlyKeys(0x9F10, 0x91);
        assertThat(Tlv.parse(encoded).get(0x9F10)).hasSize(200);
    }

    @Test
    void rejectsMalformedData() {
        assertThatThrownBy(() -> Tlv.parse(HEX.parseHex("9F"))).hasMessageContaining("truncated tag");
        assertThatThrownBy(() -> Tlv.parse(HEX.parseHex("9F26"))).hasMessageContaining("missing length");
        assertThatThrownBy(() -> Tlv.parse(HEX.parseHex("9F2608A1B2"))).hasMessageContaining("truncated");
        assertThatThrownBy(() -> Tlv.parse(HEX.parseHex("820139820139"))).hasMessageContaining("repeated");
    }
}
