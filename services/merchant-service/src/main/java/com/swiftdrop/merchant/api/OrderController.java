package com.swiftdrop.merchant.api;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.merchant.service.OrderService;
import com.swiftdrop.merchant.service.OrderView;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant/orders")
@PreAuthorize("hasRole('MERCHANT') and principal.claims['merchantStatus'] == 'APPROVED'")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateOrderRequest request) {
        return orders.create(CurrentUser.id(), key, request);
    }

    @GetMapping
    public OrderView.Page list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orders.list(CurrentUser.id(), status, page, size);
    }

    @GetMapping("/summary")
    public java.util.Map<String, Long> summary() {
        return orders.summary(CurrentUser.id());
    }

    @GetMapping("/{trackingNo}")
    public OrderView.Detail detail(@PathVariable String trackingNo) {
        return orders.detail(CurrentUser.id(), trackingNo);
    }
}
