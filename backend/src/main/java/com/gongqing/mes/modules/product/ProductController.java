package com.gongqing.mes.modules.product;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;
    private final ApiResponseFactory responses;
    private final ProductActorResolver actors;

    public ProductController(ProductService service, ApiResponseFactory responses, ProductActorResolver actors) {
        this.service = service; this.responses = responses; this.actors = actors;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('product:manage')")
    public ApiResponse<PageResult<ProductMapper.ProductView>> list(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return responses.success(service.list(page, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read') or hasAuthority('product:manage')")
    public ApiResponse<ProductMapper.ProductView> detail(@PathVariable @Positive Long id) {
        return responses.success(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<Long> create(@Valid @RequestBody ProductService.ProductRequest request, Authentication auth) {
        return responses.success(service.create(request, actors.userId(auth)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<Void> update(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductService.ProductRequest request, Authentication auth) {
        service.update(id, request, actors.userId(auth));
        return responses.success(null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:manage')")
    public ApiResponse<Void> changeStatus(@PathVariable @Positive Long id,
            @Valid @RequestBody ProductService.StatusRequest request, Authentication auth) {
        service.changeStatus(id, request, actors.userId(auth));
        return responses.success(null);
    }
}
