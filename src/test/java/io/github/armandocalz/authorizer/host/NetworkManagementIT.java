package io.github.armandocalz.authorizer.host;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.NETWORK_MANAGEMENT_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RESPONSE_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.armandocalz.authorizer.host.IsoServerConfiguration.IsoServerLifecycle;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.network.IsoClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The running application, reached over TCP like a terminal would. */
@SpringBootTest(properties = "authorizer.iso.port=0")
class NetworkManagementIT {

    @Autowired IsoServerLifecycle server;

    private IsoMessage send(IsoMessage request) throws Exception {
        try (IsoClient client = IsoClient.connect("localhost", server.port(), Duration.ofSeconds(5))) {
            return client.send(request);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"001", "002", "301"})
    void approvesSignOnSignOffAndEcho(String code) throws Exception {
        IsoMessage response = send(IsoMessage.builder("0800")
                .set(TRANSMISSION_DATE_TIME, "1015120000")
                .set(STAN, "000042")
                .set(NETWORK_MANAGEMENT_CODE, code)
                .build());

        assertThat(response.mti()).isEqualTo("0810");
        assertThat(response.require(RESPONSE_CODE)).isEqualTo("00");
        assertThat(response.require(STAN)).isEqualTo("000042");
        assertThat(response.require(TRANSMISSION_DATE_TIME)).isEqualTo("1015120000");
        assertThat(response.require(NETWORK_MANAGEMENT_CODE)).isEqualTo(code);
    }

    @Test
    void rejectsUnknownNetworkManagementCodes() throws Exception {
        IsoMessage response = send(IsoMessage.builder("0800").set(STAN, "000043").set(NETWORK_MANAGEMENT_CODE, "999").build());

        assertThat(response.require(RESPONSE_CODE)).isEqualTo("12");
    }

    @Test
    void answersUnsupportedRequestsWithInvalidTransaction() throws Exception {
        IsoMessage response = send(IsoMessage.builder("0200").set(STAN, "000044").build());

        assertThat(response.mti()).isEqualTo("0210");
        assertThat(response.require(RESPONSE_CODE)).isEqualTo("12");
        assertThat(response.require(STAN)).isEqualTo("000044");
    }
}
