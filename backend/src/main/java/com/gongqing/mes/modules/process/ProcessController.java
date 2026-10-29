package com.gongqing.mes.modules.process;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/processes")
public class ProcessController {
    private final ProcessService service;
    private final ApiResponseFactory responses;
    public ProcessController(ProcessService service, ApiResponseFactory responses) {
        this.service = service; this.responses = responses;
    }
    @GetMapping
    @PreAuthorize("hasAuthority('process:read') or hasAuthority('process:manage')")
    public ApiResponse<PageResult<ProcessMapper.ProcessView>> list(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return responses.success(service.list(page, keyword, status));
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('process:read') or hasAuthority('process:manage')")
    public ApiResponse<ProcessMapper.ProcessView> detail(@PathVariable @Positive Long id) {
        return responses.success(service.detail(id));
    }
    @PostMapping
    @PreAuthorize("hasAuthority('process:manage')")
    public ApiResponse<Long> create(@Valid @RequestBody ProcessService.ProcessRequest request) {
        return responses.success(service.create(request));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('process:manage')")
    public ApiResponse<Void> update(@PathVariable @Positive Long id,
            @Valid @RequestBody ProcessService.ProcessRequest request) {
        service.update(id, request); return responses.success(null);
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('process:manage')")
    public ApiResponse<Void> changeStatus(@PathVariable @Positive Long id,
            @Valid @RequestBody ProcessService.StatusRequest request) {
        service.changeStatus(id, request); return responses.success(null);
    }
}
