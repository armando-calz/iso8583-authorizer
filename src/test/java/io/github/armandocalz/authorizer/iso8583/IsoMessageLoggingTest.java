package io.github.armandocalz.authorizer.iso8583;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IsoMessageLoggingTest {

    @Test
    void neverPrintsFullCardDataInLogs() {
        IsoMessage message = IsoMessage.builder("0100")
                .set(Iso8583Fields.PAN, "4111111111111111")
                .set(Iso8583Fields.TRACK_2_DATA, "4111111111111111D2812101")
                .set(Iso8583Fields.ICC_DATA, "9F2608A1B2C3D4E5F60718")
                .set(Iso8583Fields.STAN, "000123")
                .build();

        assertThat(message.toString())
                .contains("2=411111******1111", "35=<redacted>", "55=<redacted>", "11=000123")
                .doesNotContain("4111111111111111", "A1B2C3D4");
    }

    @Test
    void masksShortPansCompletely() {
        assertThat(IsoMessage.maskPan("4111111111")).isEqualTo("**********");
        assertThat(IsoMessage.maskPan("4111112222333")).isEqualTo("411111***2333");
    }
}
