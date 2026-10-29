package com.gongqing.mes.modules.statistics;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.web.ApiResponseFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/statistics")
@PreAuthorize("hasAuthority('statistics:read')")
public class StatisticsController {
    private final StatisticsService service;
    private final ApiResponseFactory responses;

    public StatisticsController(StatisticsService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StatisticsService.Dashboard> dashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status) {
        return responses.success(service.dashboard(dateFrom, dateTo, productId, status));
    }

    @GetMapping("/reports")
    public ApiResponse<?> reportDetails(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status) {
        return responses.success(service.reportDetails(dateFrom, dateTo, productId, status));
    }
}
