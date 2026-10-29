package com.gongqing.mes.modules.statistics;

import org.springframework.stereotype.Service;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;

import java.time.LocalDate;
import java.util.List;

@Service
public class StatisticsService {
    private final StatisticsMapper mapper;

    public StatisticsService(StatisticsMapper mapper) { this.mapper = mapper; }

    public Dashboard dashboard(LocalDate from, LocalDate to) {
        return dashboard(from, to, null, null);
    }

    public Dashboard dashboard(LocalDate from, LocalDate to, Long productId, String orderStatus) {
        validateRange(from, to);
        validateFilters(productId, orderStatus);
        return new Dashboard(mapper.orderStats(from, to, productId, orderStatus),
                mapper.workOrderStatuses(from, to, productId, orderStatus),
                mapper.dailyProduction(from, to, productId, orderStatus),
                mapper.qualityStats(from, to, productId, orderStatus),
                mapper.planExecution(from, to, productId, orderStatus),
                mapper.productRanking(from, to, productId, orderStatus, 10));
    }

    public List<StatisticsMapper.ReportDetail> reportDetails(LocalDate from, LocalDate to) {
        return reportDetails(from, to, null, null);
    }

    public List<StatisticsMapper.ReportDetail> reportDetails(LocalDate from, LocalDate to,
                                                             Long productId, String orderStatus) {
        validateRange(from, to);
        validateFilters(productId, orderStatus);
        return mapper.reportDetails(from, to, productId, orderStatus, 500);
    }

    static void validateFilters(Long productId, String orderStatus) {
        if (productId != null && productId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "产品参数无效");
        }
        if (orderStatus != null && !orderStatus.matches("DRAFT|CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单状态不合法");
        }
    }

    static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日期范围无效");
        if (to.isAfter(from.plusDays(365)))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日期范围不能超过366天");
    }

    public record Dashboard(StatisticsMapper.OrderStats orders, List<StatisticsMapper.StatusCount> workOrderStatuses,
                            List<StatisticsMapper.DailyProduction> dailyProduction,
                            StatisticsMapper.QualityStats quality, List<StatisticsMapper.PlanExecution> planExecution,
                            List<StatisticsMapper.ProductRanking> productRanking) { }
}
