package io.github.armandocalz.authorizer.network;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RESPONSE_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IsoServerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Echoes the STAN back with the response MTI and code 00; fails on STAN 999999. */
    private static final MessageHandler ECHO = request -> {
        if (request.get(STAN).orElse("").equals("999999")) {
            throw new IllegalStateException("boom");
        }
        return Mti.responseTo(request.mti()).map(mti -> IsoMessage.builder(mti)
                .set(STAN, request.require(STAN)).set(RESPONSE_CODE, "00").build());
    };

    private IsoServer server;

    private IsoClient client() throws IOException {
        return IsoClient.connect("localhost", server.port(), TIMEOUT);
    }

    private static IsoMessage request(String mti, int stan) {
        return IsoMessage.builder(mti).set(STAN, "%06d".formatted(stan)).build();
    }

    @AfterEach
    void stop() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void answersRequestsInOrderOnOneConnection() throws Exception {
        server = IsoServer.start(0, ECHO);
        try (IsoClient client = client()) {
            for (int stan = 1; stan <= 5; stan++) {
                IsoMessage response = client.send(request("0800", stan));
                assertThat(response.mti()).isEqualTo("0810");
                assertThat(response.require(STAN)).isEqualTo("%06d".formatted(stan));
            }
        }
    }

    @Test
    void answersMalformedRequestsWithAFormatErrorAndKeepsTheConnection() throws Exception {
        server = IsoServer.start(0, ECHO);
        try (IsoClient client = client()) {
            client.sendRaw("0100ZZZZ".getBytes(StandardCharsets.US_ASCII));
            IsoMessage error = client.receive();
            assertThat(error.mti()).isEqualTo("0110");
            assertThat(error.require(RESPONSE_CODE)).isEqualTo("30");

            assertThat(client.send(request("0800", 7)).require(RESPONSE_CODE)).isEqualTo("00");
        }
    }

    @Test
    void dropsUnreadableMessagesAndResponsesButKeepsTheConnection() throws Exception {
        server = IsoServer.start(0, ECHO);
        try (IsoClient client = client()) {
            client.sendRaw("??".getBytes(StandardCharsets.US_ASCII));
            client.sendRaw(new byte[0]);
            client.sendRaw(new io.github.armandocalz.authorizer.iso8583.Iso8583Codec().encode(request("0810", 1)));

            IsoMessage next = client.send(request("0800", 2));
            assertThat(next.require(STAN)).as("the first answer is for the last request").isEqualTo("000002");
        }
    }

    @Test
    void answersSystemMalfunctionWhenTheHandlerFails() throws Exception {
        server = IsoServer.start(0, ECHO);
        try (IsoClient client = client()) {
            assertThat(client.send(request("0100", 999_999)).require(RESPONSE_CODE)).isEqualTo("96");
            assertThat(client.send(request("0100", 1)).require(RESPONSE_CODE)).isEqualTo("00");
        }
    }

    @Test
    void servesManyConcurrentConnections() throws Exception {
        server = IsoServer.start(0, ECHO);
        int connections = 500;
        int messagesEach = 10;

        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService terminals = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < connections; t++) {
                int terminal = t;
                results.add(terminals.submit(() -> {
                    int ok = 0;
                    try (IsoClient client = client()) {
                        for (int m = 0; m < messagesEach; m++) {
                            int stan = terminal * messagesEach + m;
                            if (client.send(request("0800", stan)).require(STAN).equals("%06d".formatted(stan))) {
                                ok++;
                            }
                        }
                    }
                    return ok;
                }));
            }
            int total = 0;
            for (Future<Integer> result : results) {
                total += result.get();
            }
            assertThat(total).isEqualTo(connections * messagesEach);
        }
    }

    @Test
    void closingTheServerClosesClientConnections() throws Exception {
        server = IsoServer.start(0, request -> Optional.empty());
        try (IsoClient client = client()) {
            server.close();
            server = null;

            assertThatThrownBy(client::receive).isInstanceOf(IOException.class);
        }
    }
}
