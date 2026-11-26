package ch.bonflow.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A part of the venue, e.g. terrace or hall. */
@Entity
@Table(name = "area")
public class Area extends AbstractEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
