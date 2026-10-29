package com.gongqing.mes.common.api;

import java.time.Instant;

/** Common response envelope for the REST API foundation. */
public record ApiResponse<T>(String code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId);
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data, String traceId) {
        return new ApiResponse<>(code, message, data, traceId);
    }

    public record HealthData(String status, String service, String time) {
        public static HealthData up() {
            return new HealthData("UP", "gongqing-mes-backend", Instant.now().toString());
        }
    }
}
