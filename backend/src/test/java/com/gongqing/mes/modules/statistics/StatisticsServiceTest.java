package com.gongqing.mes.modules.statistics;

import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {
    private final StatisticsMapper mapper = mock(StatisticsMapper.class);
    private final StatisticsService service = new StatisticsService(mapper);
    private final LocalDate from = LocalDate.of(2026, 10, 1);
    private final LocalDate to = LocalDate.of(2026, 10, 31);

    @Test
    void dashboardUsesEveryIndependentAggregateAndPreservesEmptyLists() {
        var orders = new StatisticsMapper.OrderStats(2L, 1L, 1L, 0L, 0L, 0L);
        var quality = new StatisticsMapper.QualityStats(0L, 0L, 0L);
        when(mapper.orderStats(from, to, null, null)).thenReturn(orders);
        when(mapper.workOrderStatuses(from, to, null, null)).thenReturn(List.of());
        when(mapper.dailyProduction(from, to, null, null)).thenReturn(List.of());
        when(mapper.qualityStats(from, to, null, null)).thenReturn(quality);
        when(mapper.planExecution(from, to, null, null)).thenReturn(List.of());
        when(mapper.productRanking(from, to, null, null, 10)).thenReturn(List.of());

        var result = service.dashboard(from, to);

        assertThat(result.orders()).isEqualTo(orders);
        assertThat(result.quality()).isEqualTo(quality);
        assertThat(result.dailyProduction()).isEmpty();
        assertThat(result.productRanking()).isEmpty();
        verify(mapper).planExecution(from, to, null, null);
    }

    @Test
    void rejectsReversedAndOverlongDateRangesBeforeQuerying() {
        assertThatThrownBy(() -> service.dashboard(to, from)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.dashboard(from, from.plusDays(366))).isInstanceOf(BusinessException.class);
    }

    @Test
    void includes366CalendarDaysAndReturnsActualDetailRows() {
        LocalDate end = from.plusDays(365);
        var detail = new StatisticsMapper.ReportDetail(from, "RP-1", "WO-1", "MO-1", "P-1", "Part", 4L, 1L, 5L, "Operator");
        when(mapper.reportDetails(from, end, null, null, 500)).thenReturn(List.of(detail));

        assertThat(service.reportDetails(from, end)).containsExactly(detail);
    }

    @Test
    void rejectsInvalidProductAndStatusFiltersBeforeQuerying() {
        assertThatThrownBy(() -> service.dashboard(from, to, 0L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.dashboard(from, to, null, "UNKNOWN")).isInstanceOf(BusinessException.class);
    }
}
