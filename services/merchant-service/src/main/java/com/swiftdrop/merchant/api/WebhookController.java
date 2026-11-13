package com.swiftdrop.merchant.api;

import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.merchant.service.WebhookService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant/webhook")
@PreAuthorize("hasRole('MERCHANT') and principal.claims['merchantStatus'] == 'APPROVED'")
public class WebhookController {
    public record Request(@NotBlank @Size(max = 2048) String url) {}

    private final WebhookService hooks;

    public WebhookController(WebhookService hooks) {
        this.hooks = hooks;
    }

    @GetMapping
    public WebhookService.Endpoint endpoint() {
        return hooks.endpoint(CurrentUser.id());
    }

    @PutMapping
    public ResponseEntity<WebhookService.NewEndpoint> configure(
            @Valid @RequestBody Request request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(hooks.configure(CurrentUser.id(), request.url()));
    }

    @PostMapping("/rotate-secret")
    public ResponseEntity<WebhookService.NewEndpoint> rotate() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(hooks.rotate(CurrentUser.id()));
    }

    @GetMapping("/deliveries")
    public List<WebhookService.Delivery> deliveries(@RequestParam(defaultValue = "50") int limit) {
        return hooks.deliveries(CurrentUser.id(), limit);
    }

    @PostMapping("/deliveries/{id}/replay")
    public ResponseEntity<Void> replay(@PathVariable UUID id) {
        hooks.replay(CurrentUser.id(), id);
        return ResponseEntity.accepted().build();
    }
}
