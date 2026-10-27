package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.authorization.AuthorizationService;
import io.github.armandocalz.authorizer.authorization.ReversalService;
import io.github.armandocalz.authorizer.emv.EmvCryptography;
import io.github.armandocalz.authorizer.network.IsoServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.armandocalz.authorizer.network.MessageHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IsoServerConfiguration {

    @Bean
    MessageRouter messageRouter(
            AuthorizationService authorizationService, ReversalService reversalService, EmvCryptography emvCryptography) {
        AuthorizationHandler authorization = new AuthorizationHandler(authorizationService, emvCryptography);
        ReversalHandler reversal = new ReversalHandler(reversalService);
        return new MessageRouter(Map.of(
                "0100", authorization,
                "0101", authorization, // repeat: a retransmission the terminal flags explicitly
                "0400", reversal,
                "0401", reversal,
                "0800", new NetworkManagementHandler()));
    }

    @Bean
    IsoServerLifecycle isoServer(
            MessageRouter messageRouter, MeterRegistry meterRegistry, @Value("${authorizer.iso.port:8583}") int port) {
        return new IsoServerLifecycle(new MeteredMessageHandler(messageRouter, meterRegistry), port);
    }

    /** Starts the TCP server with the application context and stops it on shutdown. */
    public static final class IsoServerLifecycle implements SmartLifecycle {

        private final MessageHandler handler;
        private final int port;
        private volatile IsoServer server;

        IsoServerLifecycle(MessageHandler handler, int port) {
            this.handler = handler;
            this.port = port;
        }

        @Override
        public void start() {
            try {
                server = IsoServer.start(port, handler);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot start the ISO 8583 server on port " + port, e);
            }
        }

        @Override
        public void stop() {
            try {
                server.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                server = null;
            }
        }

        @Override
        public boolean isRunning() {
            return server != null;
        }

        /** The bound port (useful when configured as 0). */
        public int port() {
            return server.port();
        }
    }
}
