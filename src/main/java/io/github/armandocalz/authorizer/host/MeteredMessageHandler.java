package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.network.MessageHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;

/**
 * Records every exchange in the {@code iso8583.messages} timer, tagged with the request MTI, the
 * response MTI and the response code (field 39). Counts per response code (approvals, each kind of
 * decline), rates and latency percentiles all come from this one meter. Card data is never a tag.
 */
public final class MeteredMessageHandler implements MessageHandler {

    static final String METER = "iso8583.messages";

    private final MessageHandler delegate;
    private final MeterRegistry registry;

    public MeteredMessageHandler(MessageHandler delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public Optional<IsoMessage> handle(IsoMessage request) {
        Timer.Sample sample = Timer.start(registry);
        String responseMti = "none";
        String responseCode = "none";
        try {
            Optional<IsoMessage> response = delegate.handle(request);
            if (response.isPresent()) {
                responseMti = response.get().mti();
                responseCode = response.get().get(Iso8583Fields.RESPONSE_CODE).orElse("none");
            }
            return response;
        } catch (RuntimeException e) {
            responseCode = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder(METER)
                    .description("ISO 8583 requests handled by the host")
                    .tag("mti", request.mti())
                    .tag("response_mti", responseMti)
                    .tag("response_code", responseCode)
                    .register(registry));
        }
    }
}
