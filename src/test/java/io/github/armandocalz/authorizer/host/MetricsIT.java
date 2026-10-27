package io.github.armandocalz.authorizer.host;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.NETWORK_MANAGEMENT_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.armandocalz.authorizer.PostgresTestcontainer;
import io.github.armandocalz.authorizer.host.IsoServerConfiguration.IsoServerLifecycle;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.network.IsoClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** Exchanges over ISO 8583 show up in the Prometheus endpoint, and health reports UP. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "authorizer.iso.port=0")
@Import(PostgresTestcontainer.class)
class MetricsIT {

    @Autowired IsoServerLifecycle server;
    @LocalServerPort int httpPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private String get(String path) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return response.body();
    }

    @Test
    void exposesExchangesPerResponseCodeForPrometheus() throws Exception {
        try (IsoClient client = IsoClient.connect("localhost", server.port(), Duration.ofSeconds(5))) {
            client.send(IsoMessage.builder("0800").set(STAN, "000001").set(NETWORK_MANAGEMENT_CODE, "301").build());
            client.send(IsoMessage.builder("0800").set(STAN, "000002").set(NETWORK_MANAGEMENT_CODE, "999").build());
        }

        String metrics = get("/actuator/prometheus");

        assertThat(metrics)
                .containsPattern("iso8583_messages_seconds_count\\{[^}]*mti=\"0800\"[^}]*response_code=\"00\"[^}]*} 1")
                .containsPattern("iso8583_messages_seconds_count\\{[^}]*mti=\"0800\"[^}]*response_code=\"12\"[^}]*} 1")
                .contains("iso8583_messages_seconds_bucket");
        assertThat(get("/actuator/health")).contains("\"status\":\"UP\"");
    }
}
