package com.gongqing.mes.modules.quality;

import com.gongqing.mes.common.api.*;
import com.gongqing.mes.common.web.ApiResponseFactory;
import com.gongqing.mes.modules.product.ProductActorResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController @RequestMapping("/api/v1/quality")
public class QualityController {
 private final QualityService service; private final ApiResponseFactory responses; private final ProductActorResolver actors;
 public QualityController(QualityService service,ApiResponseFactory responses,ProductActorResolver actors){this.service=service;this.responses=responses;this.actors=actors;}
 @GetMapping("/records") @PreAuthorize("hasAuthority('quality:read') or hasAuthority('quality:create') or hasAuthority('quality:dispose')")
 public ApiResponse<PageResult<QualityMapper.InspectionView>> page(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) @Positive Long workOrderId,@RequestParam(required=false) @Positive Long reportId,@RequestParam(required=false) @Positive Long productId,@RequestParam(required=false) String status,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dateFrom,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dateTo){return responses.success(service.page(page,workOrderId,reportId,productId,status,dateFrom,dateTo));}
 @GetMapping("/records/{id}") @PreAuthorize("hasAuthority('quality:read') or hasAuthority('quality:create') or hasAuthority('quality:dispose')")
 public ApiResponse<QualityMapper.InspectionView> detail(@PathVariable @Positive Long id){return responses.success(service.detail(id));}
 @PostMapping("/records") @PreAuthorize("hasAuthority('quality:create')")
 public ApiResponse<QualityMapper.InspectionView> create(@Valid @RequestBody QualityService.CreateRequest body,Authentication auth){return responses.success(service.create(body,actors.userId(auth)));}
 @PatchMapping("/records/{id}/disposition") @PreAuthorize("hasAuthority('quality:dispose')")
 public ApiResponse<Void> dispose(@PathVariable @Positive Long id,@Valid @RequestBody QualityService.DisposeRequest body,Authentication auth){service.dispose(id,body,actors.userId(auth));return responses.success(null);}
 @GetMapping("/filter-options") @PreAuthorize("hasAuthority('quality:read') or hasAuthority('quality:create')")
 public ApiResponse<?> options(){return responses.success(service.options());}
 @GetMapping("/work-orders/{id}/reports") @PreAuthorize("hasAuthority('quality:read') or hasAuthority('quality:create')")
 public ApiResponse<?> reports(@PathVariable @Positive Long id){return responses.success(service.reports(id));}
}
