package com.swiftdrop.dispatch.api;

import com.swiftdrop.dispatch.service.DispatchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {
    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/parcels/unassigned")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public Page<DispatchDtos.ParcelResponse> unassigned(Pageable pageable) {
        return dispatchService.unassigned(pageable);
    }

    @GetMapping("/parcels")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public Page<DispatchDtos.ParcelResponse> parcels(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) UUID courierId,
                                                     Pageable pageable) {
        return dispatchService.parcels(status, courierId, pageable);
    }

    @GetMapping("/couriers/workloads")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public List<DispatchDtos.CourierResponse> workloads() {
        return dispatchService.workloads();
    }

    @GetMapping("/parcels/{parcelId}/suggested-courier")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public DispatchDtos.CourierResponse suggested(@PathVariable UUID parcelId) {
        return dispatchService.suggestedCourier(parcelId);
    }

    @PostMapping("/parcels/{parcelId}/assign")
    @PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
    public DispatchDtos.AssignmentResponse assign(@PathVariable UUID parcelId,
                                                  @Valid @RequestBody DispatchDtos.AssignRequest request) {
        return dispatchService.assign(parcelId, request);
    }

    @PostMapping("/couriers")
    @PreAuthorize("hasRole('ADMIN')")
    public DispatchDtos.CourierResponse createCourier(@Valid @RequestBody DispatchDtos.CourierRequest request) {
        return dispatchService.createCourier(request);
    }

    @PatchMapping("/couriers/{courierId}")
    @PreAuthorize("hasRole('ADMIN')")
    public DispatchDtos.CourierResponse updateCourier(@PathVariable UUID courierId,
                                                      @RequestBody DispatchDtos.CourierPatch request) {
        return dispatchService.updateCourier(courierId, request);
    }
}
