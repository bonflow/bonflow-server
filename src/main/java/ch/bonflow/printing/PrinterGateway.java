package ch.bonflow.printing;

import java.io.IOException;

/** Delivers raw bytes to a network printer. */
interface PrinterGateway {

    /** @throws IOException when the printer cannot be reached or the transfer fails */
    void send(String host, int port, byte[] payload) throws IOException;
}
