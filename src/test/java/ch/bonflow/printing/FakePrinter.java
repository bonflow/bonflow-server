package ch.bonflow.printing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A network printer on localhost: records everything it receives, one entry per connection. */
final class FakePrinter implements AutoCloseable {

    private final ServerSocket server;
    private final List<byte[]> received = new CopyOnWriteArrayList<>();

    private FakePrinter(ServerSocket server) {
        this.server = server;
        Thread.ofVirtual().start(this::serve);
    }

    static FakePrinter start() throws IOException {
        return new FakePrinter(new ServerSocket(0, 10, InetAddress.getLoopbackAddress()));
    }

    /** Starts on a specific port, e.g. one that was unreachable before. */
    static FakePrinter start(int port) throws IOException {
        return new FakePrinter(new ServerSocket(port, 10, InetAddress.getLoopbackAddress()));
    }

    /** A port nothing listens on. */
    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }

    int port() {
        return server.getLocalPort();
    }

    List<byte[]> received() {
        return received;
    }

    private void serve() {
        while (!server.isClosed()) {
            try (var socket = server.accept()) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                socket.getInputStream().transferTo(data);
                received.add(data.toByteArray());
            } catch (IOException e) {
                // closed
            }
        }
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
