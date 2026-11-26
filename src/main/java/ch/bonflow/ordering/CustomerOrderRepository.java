package ch.bonflow.ordering;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID>, JpaSpecificationExecutor<CustomerOrder> {

    List<CustomerOrder> findAllByDiningTableIdAndStatusOrderByCreatedAt(Long diningTableId, OrderStatus status);
}
