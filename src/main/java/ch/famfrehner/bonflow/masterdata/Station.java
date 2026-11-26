package ch.famfrehner.bonflow.masterdata;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/** A place that prepares items (kitchen, bar, ...), with printers and optionally a display. */
@Entity
@Table(name = "station")
public class Station extends AbstractEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "has_display", nullable = false)
    private boolean hasDisplay = true;

    // Master data is small: load printers eagerly so views need no open session.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "station_printer",
            joinColumns = @JoinColumn(name = "station_id"),
            inverseJoinColumns = @JoinColumn(name = "printer_id"))
    private Set<Printer> printers = new LinkedHashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isHasDisplay() {
        return hasDisplay;
    }

    public void setHasDisplay(boolean hasDisplay) {
        this.hasDisplay = hasDisplay;
    }

    public Set<Printer> getPrinters() {
        return printers;
    }

    public void setPrinters(Set<Printer> printers) {
        this.printers = new LinkedHashSet<>(printers);
    }
}
