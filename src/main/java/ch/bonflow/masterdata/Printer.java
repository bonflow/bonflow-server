package ch.bonflow.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "printer")
public class Printer extends AbstractEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port = 9100;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrinterProtocol protocol = PrinterProtocol.ESC_POS;

    @Column(name = "paper_width_mm", nullable = false)
    private int paperWidthMm = 80;

    @Column(nullable = false)
    private boolean enabled = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public PrinterProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(PrinterProtocol protocol) {
        this.protocol = protocol;
    }

    public int getPaperWidthMm() {
        return paperWidthMm;
    }

    public void setPaperWidthMm(int paperWidthMm) {
        this.paperWidthMm = paperWidthMm;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
