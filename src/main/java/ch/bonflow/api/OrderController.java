package ch.bonflow.api;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.bonflow.ordering.AddItems;
import ch.bonflow.ordering.OrderResult;
import ch.bonflow.ordering.OrderService;
import ch.bonflow.ordering.OrderView;
import ch.bonflow.ordering.SubmitOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record CancelItems(@NotEmpty Set<Long> itemIds) {
    }

    @Operation(summary = "Submit an order. The client-generated id makes retries idempotent: 201 when created, "
            + "200 with the stored order when the id is already known.")
    @PostMapping("/orders")
    ResponseEntity<OrderView> submit(@Valid @RequestBody SubmitOrder request, @AuthenticationPrincipal Jwt jwt) {
        long waiterId = Long.parseLong(jwt.getSubject());
        OrderResult result;
        try {
            result = orders.submit(request, waiterId);
        } catch (DataIntegrityViolationException e) {
            // two requests with the same id raced: the other one won, return its order
            result = orders.submit(request, waiterId);
        }
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/orders/" + request.id())).body(result.order());
        }
        return ResponseEntity.ok(result.order());
    }

    @Operation(summary = "Order with items and the status of each station ticket")
    @GetMapping("/orders/{id}")
    OrderView get(@PathVariable UUID id) {
        return orders.get(id);
    }

    @Operation(summary = "Follow-up submission: adds items and creates ADDITION tickets")
    @PostMapping("/orders/{id}/items")
    ResponseEntity<OrderView> addItems(@PathVariable UUID id, @Valid @RequestBody AddItems request) {
        return ResponseEntity.status(201).body(orders.addItems(id, request));
    }

    @Operation(summary = "Cancel items; already ticketed items produce a STORNO ticket")
    @PostMapping("/orders/{id}/cancellations")
    OrderView cancel(@PathVariable UUID id, @Valid @RequestBody CancelItems request) {
        return orders.cancelItems(id, request.itemIds());
    }

    @Operation(summary = "Close the order (table is done); no further items can be added")
    @PostMapping("/orders/{id}/completion")
    OrderView complete(@PathVariable UUID id) {
        return orders.complete(id);
    }

    @Operation(summary = "Open (submitted, not yet completed) orders of a table")
    @GetMapping("/tables/{tableId}/orders")
    List<OrderView> openOrders(@PathVariable long tableId) {
        return orders.openOrdersOfTable(tableId);
    }
}
