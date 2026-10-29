package com.gongqing.mes.modules.equipment;

import com.gongqing.mes.common.api.*;
import com.gongqing.mes.common.web.ApiResponseFactory;
import com.gongqing.mes.modules.product.ProductActorResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/equipment")
public class EquipmentController {
 private final EquipmentService service;private final ApiResponseFactory responses;private final ProductActorResolver actors;
 public EquipmentController(EquipmentService service,ApiResponseFactory responses,ProductActorResolver actors){this.service=service;this.responses=responses;this.actors=actors;}
 @GetMapping @PreAuthorize("hasAuthority('equipment:read') or hasAuthority('equipment:manage') or hasAuthority('equipment:maintain')")
 public ApiResponse<PageResult<EquipmentMapper.EquipmentView>> page(@Valid @ModelAttribute PageQuery page,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status){return responses.success(service.page(page,keyword,status));}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('equipment:read') or hasAuthority('equipment:manage') or hasAuthority('equipment:maintain')")
 public ApiResponse<EquipmentMapper.EquipmentView> detail(@PathVariable @Positive Long id){return responses.success(service.detail(id));}
 @PostMapping @PreAuthorize("hasAuthority('equipment:manage')")
 public ApiResponse<Long> create(@Valid @RequestBody EquipmentService.EquipmentRequest body,Authentication auth){return responses.success(service.create(body,actors.userId(auth)));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('equipment:manage')")
 public ApiResponse<Void> update(@PathVariable @Positive Long id,@Valid @RequestBody EquipmentService.EquipmentRequest body,Authentication auth){service.update(id,body,actors.userId(auth));return responses.success(null);}
 @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('equipment:manage')")
 public ApiResponse<Void> status(@PathVariable @Positive Long id,@Valid @RequestBody EquipmentService.StatusRequest body,Authentication auth){service.changeStatus(id,body,actors.userId(auth));return responses.success(null);}
 @GetMapping("/{id}/maintenance-records") @PreAuthorize("hasAuthority('equipment:read') or hasAuthority('equipment:maintain')")
 public ApiResponse<PageResult<EquipmentMapper.MaintenanceView>> maintenance(@PathVariable @Positive Long id,@Valid @ModelAttribute PageQuery page){return responses.success(service.maintenance(id,page));}
 @PostMapping("/{id}/maintenance-records") @PreAuthorize("hasAuthority('equipment:maintain')")
 public ApiResponse<Long> startMaintenance(@PathVariable @Positive Long id,@Valid @RequestBody EquipmentService.StartMaintenanceRequest body,Authentication auth){return responses.success(service.startMaintenance(id,body,actors.userId(auth)));}
 @PatchMapping("/{id}/maintenance-records/{recordId}/complete") @PreAuthorize("hasAuthority('equipment:maintain')")
 public ApiResponse<Void> complete(@PathVariable @Positive Long id,@PathVariable @Positive Long recordId,@Valid @RequestBody EquipmentService.CompleteMaintenanceRequest body,Authentication auth){service.completeMaintenance(id,recordId,body,actors.userId(auth));return responses.success(null);}
}
