package com.gongqing.mes.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/** Shared pagination output for REST endpoints. */
public record PageResult<T>(List<T> records, long page, long size, long total) {

    public static <T> PageResult<T> from(IPage<T> source) {
        return new PageResult<>(source.getRecords(), source.getCurrent(), source.getSize(), source.getTotal());
    }
}
