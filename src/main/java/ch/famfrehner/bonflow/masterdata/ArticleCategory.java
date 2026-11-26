package ch.famfrehner.bonflow.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "article_category")
public class ArticleCategory extends AbstractEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Items of this category go to this station unless the article overrides it. */
    @ManyToOne
    @JoinColumn(name = "default_station_id")
    private Station defaultStation;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private ArticleCategory parent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Station getDefaultStation() {
        return defaultStation;
    }

    public void setDefaultStation(Station defaultStation) {
        this.defaultStation = defaultStation;
    }

    public ArticleCategory getParent() {
        return parent;
    }

    public void setParent(ArticleCategory parent) {
        this.parent = parent;
    }
}
