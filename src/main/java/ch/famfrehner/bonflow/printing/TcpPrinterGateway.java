package ch.famfrehner.bonflow.printing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.stereotype.Component;

/** Raw TCP ("JetDirect", port 9100), as spoken by Epson TM network printers. */
@Component
class TcpPrinterGateway implements PrinterGateway {

    private final PrintingProperties props;

    TcpPrinterGateway(PrintingProperties props) {
        this.props = props;
    }

    @Override
    public void send(String host, int port, byte[] payload) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) props.connectTimeout().toMillis());
            socket.setSoTimeout((int) props.writeTimeout().toMillis());
            var out = socket.getOutputStream();
            out.write(payload);
            out.flush();
            socket.shutdownOutput();
        }
    }
}
