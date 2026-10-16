package io.github.armandocalz.authorizer.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MtiTest {

    @ParameterizedTest
    @CsvSource({"0100,0110", "0200,0210", "0400,0410", "0420,0430", "0800,0810"})
    void mapsRequestsAndAdvicesToTheirResponse(String request, String response) {
        assertThat(Mti.responseTo(request)).hasValue(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0110", "0430", "0810", "01X0", "010", ""})
    void hasNoResponseForResponsesOrMalformedMtis(String mti) {
        assertThat(Mti.responseTo(mti)).isEmpty();
    }
}
