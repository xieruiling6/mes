package com.gongqing.mes.health;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.api.ApiResponse.HealthData;
import com.gongqing.mes.common.web.ApiResponseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final ApiResponseFactory responseFactory;

    public HealthController(ApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @GetMapping("/health")
    public ApiResponse<HealthData> health() {
        return responseFactory.success(HealthData.up());
    }
}
