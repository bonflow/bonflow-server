package ch.famfrehner.bonflow.masterdata;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** E.g. "Cooking: rare / medium / well done" or "Extras". */
@Entity
@Table(name = "modifier_group")
public class ModifierGroup extends AbstractEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "multi_select", nullable = false)
    private boolean multiSelect;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder")
    private List<Modifier> modifiers = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void setMultiSelect(boolean multiSelect) {
        this.multiSelect = multiSelect;
    }

    public List<Modifier> getModifiers() {
        return List.copyOf(modifiers);
    }

    public List<String> getModifierNames() {
        return modifiers.stream().map(Modifier::getName).toList();
    }

    /**
     * Replaces the modifiers by the given ordered names. Existing rows are reused by position, so
     * ids stay stable (later order items reference them) when only names change.
     */
    public void setModifierNames(List<String> names) {
        for (int i = 0; i < names.size(); i++) {
            if (i < modifiers.size()) {
                modifiers.get(i).update(names.get(i), i);
            } else {
                modifiers.add(new Modifier(this, names.get(i), i));
            }
        }
        while (modifiers.size() > names.size()) {
            modifiers.remove(modifiers.size() - 1);
        }
    }
}
