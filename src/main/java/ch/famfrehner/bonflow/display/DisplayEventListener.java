package ch.famfrehner.bonflow.display;

import ch.famfrehner.bonflow.ordering.OrderSubmitted;
import ch.famfrehner.bonflow.routing.TicketStatusChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes changes to open station views once the change is committed. */
@Component
class DisplayEventListener {

    private final StationBroadcaster broadcaster;

    DisplayEventListener(StationBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(OrderSubmitted event) {
        event.stationIds().forEach(broadcaster::broadcast);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TicketStatusChanged event) {
        broadcaster.broadcast(event.stationId());
    }
}
