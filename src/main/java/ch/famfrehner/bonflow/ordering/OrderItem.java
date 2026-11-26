package ch.famfrehner.bonflow.ordering;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_item")
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private int quantity;

    @Column(columnDefinition = "text")
    private String note;

    private Integer course;

    @Column(name = "submission_no", nullable = false)
    private int submissionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status = ItemStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_item_modifier", joinColumns = @JoinColumn(name = "order_item_id"))
    @Column(name = "modifier_id")
    private Set<Long> modifierIds = new LinkedHashSet<>();

    protected OrderItem() {
    }

    OrderItem(CustomerOrder order, long articleId, int quantity, String note, Integer course, int submissionNo,
            Set<Long> modifierIds) {
        this.order = order;
        this.articleId = articleId;
        this.quantity = quantity;
        this.note = note;
        this.course = course;
        this.submissionNo = submissionNo;
        this.modifierIds = new LinkedHashSet<>(modifierIds);
    }

    void cancel() {
        status = ItemStatus.CANCELLED;
    }

    Long getId() {
        return id;
    }

    Long getArticleId() {
        return articleId;
    }

    int getQuantity() {
        return quantity;
    }

    String getNote() {
        return note;
    }

    Integer getCourse() {
        return course;
    }

    int getSubmissionNo() {
        return submissionNo;
    }

    ItemStatus getStatus() {
        return status;
    }

    Set<Long> getModifierIds() {
        return modifierIds;
    }
}
