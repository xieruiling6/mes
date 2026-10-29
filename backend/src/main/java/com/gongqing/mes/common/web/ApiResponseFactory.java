package com.gongqing.mes.common.web;

import com.gongqing.mes.common.api.ApiResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseFactory {

    public <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }
}
