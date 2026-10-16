package io.github.armandocalz.authorizer.network;

import io.github.armandocalz.authorizer.iso8583.Iso8583Codec;
import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP server for length-prefixed ISO 8583 messages. Each connection runs on its own virtual
 * thread and processes its messages in order: read, decode, handle, encode, write.
 *
 * <p>A message that cannot be decoded gets a response with code 30 (format error) when its MTI
 * is readable and is a request or advice; otherwise it is dropped. Either way the connection
 * stays open, so one bad message does not disconnect a terminal.
 */
public final class IsoServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IsoServer.class);

    private final Iso8583Codec codec = new Iso8583Codec();
    private final MessageHandler handler;
    private final ServerSocket serverSocket;
    private final ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private final Thread acceptor;

    private IsoServer(MessageHandler handler, ServerSocket serverSocket) {
        this.handler = handler;
        this.serverSocket = serverSocket;
        this.acceptor = Thread.ofVirtual().name("iso-acceptor").unstarted(this::acceptLoop);
    }

    /** Binds to {@code port} (0 for an ephemeral port) and starts accepting connections. */
    public static IsoServer start(int port, MessageHandler handler) throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        IsoServer server = new IsoServer(handler, serverSocket);
        server.acceptor.start();
        log.info("ISO 8583 server listening on port {}", server.port());
        return server;
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                openSockets.add(socket);
                connections.submit(() -> serve(socket));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log.warn("Failed to accept a connection", e);
                }
            }
        }
    }

    private void serve(Socket socket) {
        String peer = socket.getRemoteSocketAddress().toString();
        log.debug("Connection opened: {}", peer);
        try (socket;
             InputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
            Optional<byte[]> frame;
            while ((frame = Framing.read(in)).isPresent()) {
                Optional<IsoMessage> response = respond(frame.get(), peer);
                if (response.isPresent()) {
                    Framing.write(out, codec.encode(response.get()));
                }
            }
        } catch (SocketException e) {
            log.debug("Connection {} closed: {}", peer, e.getMessage());
        } catch (IOException e) {
            log.warn("Connection {} failed: {}", peer, e.getMessage());
        } finally {
            openSockets.remove(socket);
            log.debug("Connection closed: {}", peer);
        }
    }

    private Optional<IsoMessage> respond(byte[] frame, String peer) {
        IsoMessage request;
        try {
            request = codec.decode(frame);
        } catch (IsoFormatException e) {
            log.warn("Malformed message from {}: {}", peer, e.getMessage());
            return formatErrorResponse(frame);
        }
        log.info("<- {} {}", peer, request);
        try {
            Optional<IsoMessage> response = handler.handle(request);
            response.ifPresent(r -> log.info("-> {} {}", peer, r));
            return response;
        } catch (RuntimeException e) {
            log.error("Handler failed for {}", request, e);
            return Mti.responseTo(request.mti()).map(mti -> IsoMessage.builder(mti)
                    .set(Iso8583Fields.RESPONSE_CODE, ResponseCode.SYSTEM_MALFUNCTION.code())
                    .build());
        }
    }

    private static Optional<IsoMessage> formatErrorResponse(byte[] frame) {
        if (frame.length < 4) {
            return Optional.empty();
        }
        String mti = new String(frame, 0, 4, StandardCharsets.US_ASCII);
        return Mti.responseTo(mti).map(responseMti -> IsoMessage.builder(responseMti)
                .set(Iso8583Fields.RESPONSE_CODE, ResponseCode.FORMAT_ERROR.code())
                .build());
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        for (Socket socket : openSockets) {
            socket.close();
        }
        connections.close();
        log.info("ISO 8583 server on port {} stopped", serverSocket.getLocalPort());
    }
}
