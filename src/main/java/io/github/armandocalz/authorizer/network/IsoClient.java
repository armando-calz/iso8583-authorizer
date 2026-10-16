package io.github.armandocalz.authorizer.network;

import io.github.armandocalz.authorizer.iso8583.Iso8583Codec;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/** A blocking client for one connection: send a request, wait for its response. Not thread-safe. */
public final class IsoClient implements AutoCloseable {

    private final Iso8583Codec codec = new Iso8583Codec();
    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private IsoClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
    }

    public static IsoClient connect(String host, int port, Duration timeout) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
        socket.setSoTimeout((int) timeout.toMillis());
        socket.setTcpNoDelay(true);
        return new IsoClient(socket);
    }

    public IsoMessage send(IsoMessage request) throws IOException {
        Framing.write(out, codec.encode(request));
        return receive();
    }

    /** Sends raw bytes as one frame (for testing malformed messages). */
    public void sendRaw(byte[] message) throws IOException {
        Framing.write(out, message);
    }

    public IsoMessage receive() throws IOException {
        byte[] frame = Framing.read(in).orElseThrow(() -> new EOFException("Server closed the connection"));
        return codec.decode(frame);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
