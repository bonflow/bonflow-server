package ch.famfrehner.bonflow.ordering;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.famfrehner.bonflow.routing.TicketSummary;

/** Order with its items and the state of every station ticket ("is it ready to serve?"). */
public record OrderView(UUID id, long tableId, long waiterId, OrderStatus status, Instant createdAt,
        Instant submittedAt, String note, List<ItemView> items, List<TicketSummary> tickets) {

    public record ItemView(long id, long articleId, int quantity, String note, Integer course, int submissionNo,
            ItemStatus status, Set<Long> modifierIds) {

        static ItemView of(OrderItem i) {
            return new ItemView(i.getId(), i.getArticleId(), i.getQuantity(), i.getNote(), i.getCourse(),
                    i.getSubmissionNo(), i.getStatus(), i.getModifierIds());
        }
    }
}
