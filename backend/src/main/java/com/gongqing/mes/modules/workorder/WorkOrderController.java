package com.gongqing.mes.modules.workorder;

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

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
    private final WorkOrderService service;
    private final ApiResponseFactory responses;
    private final ProductActorResolver actors;

    public WorkOrderController(WorkOrderService service, ApiResponseFactory responses, ProductActorResolver actors) {
        this.service = service;
        this.responses = responses;
        this.actors = actors;
    }

    @GetMapping("/available-plans")
    @PreAuthorize("hasAuthority('workorder:read') or hasAuthority('workorder:manage')")
    public ApiResponse<java.util.List<WorkOrderMapper.AvailablePlan>> availablePlans() {
        return responses.success(service.availablePlans());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('workorder:read') or hasAuthority('workorder:manage')")
    public ApiResponse<PageResult<WorkOrderMapper.WorkOrderView>> list(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return responses.success(service.list(page, workOrderNo, productId, status, dateFrom, dateTo));
    }

    @PostMapping("/from-plan/{planId}")
    @PreAuthorize("hasAuthority('workorder:manage')")
    public ApiResponse<Long> createFromPlan(@PathVariable @Positive Long planId,
            @Valid @RequestBody WorkOrderService.CreateFromPlanRequest request, Authentication authentication) {
        return responses.success(service.createFromPlan(planId, request, actors.userId(authentication)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workorder:read') or hasAuthority('workorder:manage')")
    public ApiResponse<WorkOrderMapper.WorkOrderView> detail(@PathVariable @Positive Long id) {
        return responses.success(service.detail(id));
    }

    @GetMapping("/{id}/reports")
    @PreAuthorize("hasAuthority('workorder:read') or hasAuthority('workorder:manage') or hasAuthority('report:read')")
    public ApiResponse<PageResult<WorkOrderMapper.ReportView>> reports(@PathVariable @Positive Long id,
            @Valid @ModelAttribute PageQuery page) {
        return responses.success(service.reports(id, page));
    }

    @PatchMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('workorder:manage')")
    public ApiResponse<Void> dispatch(@PathVariable @Positive Long id,
            @Valid @RequestBody WorkOrderService.StatusRequest request, Authentication authentication) {
        service.dispatch(id, request, actors.userId(authentication));
        return responses.success(null);
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAuthority('workorder:manage')")
    public ApiResponse<Void> start(@PathVariable @Positive Long id,
            @Valid @RequestBody WorkOrderService.StatusRequest request, Authentication authentication) {
        service.start(id, request, actors.userId(authentication));
        return responses.success(null);
    }

    @PatchMapping("/{id}/pause")
    @PreAuthorize("hasAuthority('workorder:manage')")
    public ApiResponse<Void> pause(@PathVariable @Positive Long id,
            @Valid @RequestBody WorkOrderService.StatusRequest request, Authentication authentication) {
        service.pause(id, request, actors.userId(authentication));
        return responses.success(null);
    }

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('workorder:manage')")
    public ApiResponse<Void> resume(@PathVariable @Positive Long id,
            @Valid @RequestBody WorkOrderService.StatusRequest request, Authentication authentication) {
        service.resume(id, request, actors.userId(authentication));
        return responses.success(null);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('workorder:manage')")
    public ApiResponse<Void> complete(@PathVariable @Positive Long id,
            @Valid @RequestBody WorkOrderService.StatusRequest request, Authentication authentication) {
        service.complete(id, request, actors.userId(authentication));
        return responses.success(null);
    }
}
