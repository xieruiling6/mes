package com.gongqing.mes.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Shared pagination input. Sort fields must be validated by the calling query. */
public class PageQuery {

    @Min(value = 1, message = "页码必须大于等于 1")
    private long page = 1;

    @Min(value = 1, message = "每页条数必须大于等于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private long size = 20;

    @Size(max = 64, message = "排序字段长度不能超过 64")
    private String sortBy;

    @NotBlank(message = "排序方向不能为空")
    @Pattern(regexp = "(?i)asc|desc", message = "排序方向只能为 asc 或 desc")
    private String sortDirection = "asc";

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
}
