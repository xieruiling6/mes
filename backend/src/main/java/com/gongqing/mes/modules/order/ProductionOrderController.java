package com.gongqing.mes.modules.order;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.web.ApiResponseFactory;
import com.gongqing.mes.modules.product.ProductActorResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/orders")
public class ProductionOrderController {
    private final ProductionOrderService service;
    private final ApiResponseFactory responses;
    private final ProductActorResolver actors;

    public ProductionOrderController(ProductionOrderService service, ApiResponseFactory responses,
                                     ProductActorResolver actors) {
        this.service = service;
        this.responses = responses;
        this.actors = actors;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:read') or hasAuthority('order:manage')")
    public ApiResponse<PageResult<ProductionOrderMapper.OrderView>> list(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) String orderNo, @RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dueDateFrom,
            @RequestParam(required = false) LocalDate dueDateTo) {
        return responses.success(service.list(page, orderNo, productId, status, dueDateFrom, dueDateTo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:read') or hasAuthority('order:manage')")
    public ApiResponse<ProductionOrderMapper.OrderView> detail(@PathVariable @Positive Long id) {
        return responses.success(service.detail(id));
    }

    @GetMapping("/{id}/execution")
    @PreAuthorize("hasAuthority('order:read') or hasAuthority('order:manage')")
    public ApiResponse<ProductionOrderMapper.OrderExecutionView> execution(@PathVariable @Positive Long id) {
        return responses.success(service.execution(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:manage')")
    public ApiResponse<Long> create(@Valid @RequestBody ProductionOrderService.OrderRequest request,
                                    Authentication authentication) {
        return responses.success(service.create(request, actors.userId(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('order:manage')")
    public ApiResponse<Void> update(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductionOrderService.UpdateOrderRequest request, Authentication authentication) {
        service.update(id, request, actors.userId(authentication));
        return responses.success(null);
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('order:confirm')")
    public ApiResponse<Void> confirm(@PathVariable @Positive Long id, Authentication authentication) {
        service.confirm(id, actors.userId(authentication));
        return responses.success(null);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    public ApiResponse<Void> cancel(@PathVariable @Positive Long id, Authentication authentication) {
        service.cancel(id, actors.userId(authentication));
        return responses.success(null);
    }
}
