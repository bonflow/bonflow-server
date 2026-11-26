package ch.famfrehner.bonflow.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "modifier")
public class Modifier extends AbstractEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ModifierGroup group;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Modifier() {
    }

    Modifier(ModifierGroup group, String name, int sortOrder) {
        this.group = group;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    void update(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public ModifierGroup getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
