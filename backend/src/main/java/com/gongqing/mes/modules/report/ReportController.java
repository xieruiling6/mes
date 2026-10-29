package com.gongqing.mes.modules.report;

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
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService service;
    private final ApiResponseFactory responses;
    private final ProductActorResolver actors;

    public ReportController(ReportService service, ApiResponseFactory responses, ProductActorResolver actors) {
        this.service = service;
        this.responses = responses;
        this.actors = actors;
    }

    @GetMapping("/available-work-orders")
    @PreAuthorize("hasAuthority('report:create')")
    public ApiResponse<?> availableWorkOrders() {
        return responses.success(service.availableWorkOrders());
    }

    @GetMapping("/filter-options")
    @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:read:own') or hasAuthority('report:create')")
    public ApiResponse<ReportService.FilterOptions> filterOptions(Authentication authentication) {
        boolean readAll = authentication.getAuthorities().stream().anyMatch(authority ->
                "report:read".equals(authority.getAuthority()));
        Long reporterId = readAll ? null : actors.userId(authentication);
        return responses.success(service.filterOptions(reporterId, readAll));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('report:create')")
    public ApiResponse<ReportMapper.ReportView> submit(@Valid @RequestBody ReportService.SubmitReportRequest request,
            Authentication authentication) {
        return responses.success(service.submit(request, actors.userId(authentication)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('report:read:own') or hasAuthority('report:create')")
    public ApiResponse<PageResult<ReportMapper.ReportView>> mine(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) @Positive Long workOrderId,
            @RequestParam(required = false) String reportStatus, Authentication authentication) {
        return responses.success(service.mine(page, dateFrom, dateTo, productId, workOrderId, reportStatus,
                actors.userId(authentication)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('report:read')")
    public ApiResponse<PageResult<ReportMapper.ReportView>> search(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) @Positive Long workOrderId,
            @RequestParam(required = false) @Positive Long reporterId,
            @RequestParam(required = false) String reportStatus) {
        return responses.success(service.search(page, dateFrom, dateTo, productId, workOrderId, reporterId, reportStatus));
    }

    @GetMapping("/work-orders/{workOrderId}/summary")
    @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:create') or hasAuthority('report:read:own')")
    public ApiResponse<ReportMapper.WorkOrderSummary> summary(@PathVariable @Positive Long workOrderId) {
        return responses.success(service.workOrderSummary(workOrderId));
    }

    @PatchMapping("/{id}/void")
    @PreAuthorize("hasAuthority('report:void')")
    public ApiResponse<Void> voidReport(@PathVariable @Positive Long id,
            @Valid @RequestBody ReportService.VoidReportRequest request, Authentication authentication) {
        service.voidReport(id, request, actors.userId(authentication));
        return responses.success(null);
    }
}
