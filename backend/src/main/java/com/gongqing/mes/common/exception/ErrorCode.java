package com.gongqing.mes.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "请求参数不合法", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    BUSINESS_CONFLICT("BUSINESS_CONFLICT", "当前操作与资源状态冲突", HttpStatus.CONFLICT),
    DATA_CONFLICT("DATA_CONFLICT", "数据冲突，请刷新后重试", HttpStatus.CONFLICT),
    DATABASE_ERROR("DATABASE_ERROR", "数据库操作失败", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务暂时不可用", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(String code, String defaultMessage, HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
