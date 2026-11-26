package ch.bonflow.routing;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TicketRepository extends JpaRepository<StationTicket, Long> {

    List<StationTicket> findAllByOrderIdOrderById(UUID orderId);

    List<StationTicket> findAllByStationIdAndStatusNotOrderById(Long stationId, TicketStatus status);

    List<StationTicket> findByStationIdAndStatusOrderByDoneAtDescIdDesc(Long stationId, TicketStatus status, Pageable page);

    /** Station each (non-storno) ticketed item went to. */
    @Query("""
            select new ch.bonflow.routing.ItemStation(i, t.stationId)
            from StationTicket t join t.itemIds i
            where i in :itemIds and t.kind <> ch.bonflow.routing.TicketKind.STORNO
            """)
    List<ItemStation> findStationsOfItems(@Param("itemIds") Collection<Long> itemIds);
}
