package io.github.armandocalz.authorizer.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MeteredMessageHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private long count(String mti, String responseMti, String responseCode) {
        var timer = registry.find(MeteredMessageHandler.METER)
                .tags("mti", mti, "response_mti", responseMti, "response_code", responseCode).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void countsExchangesByMtiAndResponseCode() {
        MeteredMessageHandler handler = new MeteredMessageHandler(request -> Optional.of(
                IsoMessage.builder("0110").set(Iso8583Fields.RESPONSE_CODE, request.require(Iso8583Fields.STAN).equals("000001") ? "00" : "51").build()),
                registry);

        handler.handle(IsoMessage.builder("0100").set(Iso8583Fields.STAN, "000001").build());
        handler.handle(IsoMessage.builder("0100").set(Iso8583Fields.STAN, "000001").build());
        handler.handle(IsoMessage.builder("0100").set(Iso8583Fields.STAN, "000002").build());

        assertThat(count("0100", "0110", "00")).isEqualTo(2);
        assertThat(count("0100", "0110", "51")).isEqualTo(1);
    }

    @Test
    void recordsDroppedMessagesAndFailures() {
        new MeteredMessageHandler(request -> Optional.empty(), registry).handle(IsoMessage.builder("0110").build());
        MeteredMessageHandler failing = new MeteredMessageHandler(request -> {
            throw new IllegalStateException("boom");
        }, registry);

        assertThatThrownBy(() -> failing.handle(IsoMessage.builder("0100").build())).hasMessage("boom");
        assertThat(count("0110", "none", "none")).isEqualTo(1);
        assertThat(count("0100", "none", "error")).isEqualTo(1);
    }
}
