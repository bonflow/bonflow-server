package ch.bonflow.masterdata;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "article")
public class Article extends AbstractEntity {

    @Column(nullable = false, length = 200)
    private String name;

    /** Short name for tickets. */
    @Column(name = "short_name", length = 50)
    private String shortName;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ArticleCategory category;

    /** Informational only, payment is handled outside this system. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    /** Temporarily not available: stays on the menu but cannot be ordered. */
    @Column(name = "sold_out", nullable = false)
    private boolean soldOut;

    @ManyToOne
    @JoinColumn(name = "station_override_id")
    private Station stationOverride;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "article_modifier_group",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<ModifierGroup> modifierGroups = new LinkedHashSet<>();

    /** Routing rule: {@code stationOverride ?? category.defaultStation}; null if neither is set. */
    public Station resolveStation() {
        return stationOverride != null ? stationOverride : category.getDefaultStation();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public ArticleCategory getCategory() {
        return category;
    }

    public void setCategory(ArticleCategory category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isSoldOut() {
        return soldOut;
    }

    public void setSoldOut(boolean soldOut) {
        this.soldOut = soldOut;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Station getStationOverride() {
        return stationOverride;
    }

    public void setStationOverride(Station stationOverride) {
        this.stationOverride = stationOverride;
    }

    public Set<ModifierGroup> getModifierGroups() {
        return modifierGroups;
    }

    public void setModifierGroups(Set<ModifierGroup> modifierGroups) {
        this.modifierGroups = new LinkedHashSet<>(modifierGroups);
    }
}
