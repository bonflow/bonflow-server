package ch.famfrehner.bonflow.ordering;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    List<CustomerOrder> findAllByDiningTableIdAndStatusOrderByCreatedAt(Long diningTableId, OrderStatus status);
}
