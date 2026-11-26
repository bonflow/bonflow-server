package ch.bonflow.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dining_table")
public class DiningTable extends AbstractEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Column(nullable = false, unique = true, length = 50)
    private String label;

    @Column(nullable = false)
    private boolean active = true;

    /** Optional button colour as {@code #RRGGBB}; without one the area's colour applies. */
    @Column(length = 7)
    private String color;

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = ColorCodes.normalize(color);
    }

    public Area getArea() {
        return area;
    }

    public void setArea(Area area) {
        this.area = area;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
