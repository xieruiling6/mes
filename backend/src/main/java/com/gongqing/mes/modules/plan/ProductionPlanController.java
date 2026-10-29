package com.gongqing.mes.modules.plan;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.web.ApiResponseFactory;
import com.gongqing.mes.modules.product.ProductActorResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
public class ProductionPlanController {
    private final ProductionPlanService service;
    private final ApiResponseFactory responses;
    private final ProductActorResolver actors;

    public ProductionPlanController(ProductionPlanService service, ApiResponseFactory responses, ProductActorResolver actors) {
        this.service = service; this.responses = responses; this.actors = actors;
    }

    @GetMapping("/available-orders")
    @PreAuthorize("hasAuthority('plan:read') or hasAuthority('plan:manage')")
    public ApiResponse<List<ProductionPlanMapper.AvailableOrder>> availableOrders() {
        return responses.success(service.availableOrders());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('plan:read') or hasAuthority('plan:manage')")
    public ApiResponse<PageResult<ProductionPlanMapper.PlanView>> list(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) String planNo, @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) @Positive Long productId, @RequestParam(required = false) String status,
            @RequestParam(required = false) @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(32767) Integer priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDateTo) {
        return responses.success(service.list(page, planNo, orderNo, productId, status, priority,
                startDateFrom, startDateTo, endDateFrom, endDateTo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:read') or hasAuthority('plan:manage')")
    public ApiResponse<ProductionPlanMapper.PlanView> detail(@PathVariable @Positive Long id) {
        return responses.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('plan:manage')")
    public ApiResponse<Long> create(@Valid @RequestBody ProductionPlanService.CreatePlanRequest request, Authentication auth) {
        return responses.success(service.create(request, actors.userId(auth)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:manage')")
    public ApiResponse<Void> update(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductionPlanService.UpdatePlanRequest request, Authentication auth) {
        service.update(id, request, actors.userId(auth)); return responses.success(null);
    }

    @PatchMapping("/{id}/release")
    @PreAuthorize("hasAuthority('plan:publish')")
    public ApiResponse<Void> release(@PathVariable @Positive Long id, @Valid @RequestBody VersionRequest request, Authentication auth) {
        service.release(id, request.expectedUpdatedAt(), actors.userId(auth)); return responses.success(null);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('plan:cancel')")
    public ApiResponse<Void> cancel(@PathVariable @Positive Long id, @Valid @RequestBody VersionRequest request, Authentication auth) {
        service.cancel(id, request.expectedUpdatedAt(), actors.userId(auth)); return responses.success(null);
    }

    public record VersionRequest(@jakarta.validation.constraints.NotNull LocalDateTime expectedUpdatedAt) { }
}
