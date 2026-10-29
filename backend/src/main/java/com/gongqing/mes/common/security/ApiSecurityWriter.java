package com.gongqing.mes.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiSecurityWriter {
    private final ObjectMapper objectMapper;

    public ApiSecurityWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(
                code, message, null, traceId(request, response)));
    }

    private String traceId(HttpServletRequest request, HttpServletResponse response) {
        String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        if (responseTraceId != null && !responseTraceId.isBlank()) return responseTraceId;
        String requestTraceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        if (requestTraceId != null && requestTraceId.matches("[A-Za-z0-9._-]{1,100}")) return requestTraceId;
        return TraceIdFilter.currentTraceId();
    }
}
