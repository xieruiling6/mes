package com.gongqing.mes.modules.report;

import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final ReportMapper mapper = mock(ReportMapper.class);
    private final ReportAuditMapper audit = mock(ReportAuditMapper.class);
    private final ReportService service = new ReportService(mapper, audit);
    private final LocalDate reportDate = LocalDate.parse("2026-10-22");

    @Test
    void submitsReportAndAuditsOnlyAfterLockingAndCheckingQuantity() {
        var request = request(UUID.randomUUID().toString(), 10L, 20L, 17L, 3L);
        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "IN_PROGRESS"));
        when(mapper.sumValidQty(10L)).thenReturn(30L);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            var record = invocation.<ReportMapper.ReportRecord>getArgument(0);
            record.setId(81L);
            assertThat(record.getReporterId()).isEqualTo(7L);
            assertThat(record.getWorkOrderId()).isEqualTo(10L);
            return 1;
        });
        var view = view(request, 7L);
        when(mapper.findByRequestKey(request.requestKey())).thenReturn(null, null, view);

        assertThat(service.submit(request, 7L)).isSameAs(view);
        verify(mapper).lockWorkOrder(10L);
        verify(mapper).sumValidQty(10L);
        verify(audit).insert(eq(7L), eq("REPORT_CREATE"), eq(81L),
                argThat(detail -> detail.startsWith("提交报工 RP") && detail.endsWith("，工单 WO-10")));
    }

    @Test
    void rejectsOverReportingWithoutInserting() {
        var request = request(UUID.randomUUID().toString(), 10L, 21L, 20L, 1L);
        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "IN_PROGRESS"));
        when(mapper.sumValidQty(10L)).thenReturn(30L);

        assertThatThrownBy(() -> service.submit(request, 7L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过");
        verify(mapper, never()).insert(any());
        verifyNoInteractions(audit);
    }

    @Test
    void returnsExistingRecordForSameRequestAndRejectsKeyReuseWithDifferentPayload() {
        var request = request(UUID.randomUUID().toString(), 10L, 20L, 18L, 2L);
        var existing = view(request, 7L);
        when(mapper.findByRequestKey(request.requestKey())).thenReturn(existing);

        assertThat(service.submit(request, 7L)).isSameAs(existing);
        verify(mapper, never()).lockWorkOrder(any());
        assertThatThrownBy(() -> service.submit(request(UUID.fromString(request.requestKey()).toString(),
                10L, 20L, 19L, 1L), 7L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他报工数据");
    }

    @Test
    void handlesConcurrentDuplicateRequestKeyByReturningCommittedReport() {
        var request = request(UUID.randomUUID().toString(), 10L, 20L, 18L, 2L);
        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "IN_PROGRESS"));
        when(mapper.sumValidQty(10L)).thenReturn(0L);
        var existing = view(request, 7L);
        when(mapper.findByRequestKey(request.requestKey())).thenReturn(null, existing);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("unique request key"));

        assertThat(service.submit(request, 7L)).isSameAs(existing);
        verifyNoInteractions(audit);
    }

    @Test
    void checksRequestKeyAgainAfterSerializingOnWorkOrderLock() {
        var request = request(UUID.randomUUID().toString(), 10L, 50L, 50L, 0L);
        var existing = view(request, 7L);
        when(mapper.findByRequestKey(request.requestKey())).thenReturn(null, existing);
        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "IN_PROGRESS"));

        assertThat(service.submit(request, 7L)).isSameAs(existing);
        verify(mapper, never()).sumValidQty(any());
        verify(mapper, never()).insert(any());
    }

    @Test
    void rejectsIllegalQuantitiesAndWorkOrderStates() {
        var inconsistent = request(UUID.randomUUID().toString(), 10L, 10L, 7L, 2L);
        assertThatThrownBy(() -> service.submit(inconsistent, 7L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("之和");
        var zero = request(UUID.randomUUID().toString(), 10L, 0L, 0L, 0L);
        assertThatThrownBy(() -> service.submit(zero, 7L)).isInstanceOf(BusinessException.class);
        var negative = request(UUID.randomUUID().toString(), 10L, 1L, -1L, 2L);
        assertThatThrownBy(() -> service.submit(negative, 7L)).isInstanceOf(BusinessException.class);

        var notStarted = request(UUID.randomUUID().toString(), 10L, 1L, 1L, 0L);
        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "PAUSED"));
        assertThatThrownBy(() -> service.submit(notStarted, 7L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产中的工单");
        verify(mapper, never()).insert(any());
    }

    @Test
    void voidsInsteadOfChangingHistoricalQuantitiesAndRejectsTerminalWorkOrderCorrections() {
        var request = request(UUID.randomUUID().toString(), 10L, 20L, 18L, 2L);
        var report = new ReportMapper.LockedReport(81L, "RP", 10L, "VALID");
        when(mapper.lockReport(81L)).thenReturn(report);
        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "IN_PROGRESS"));
        when(mapper.voidReport(81L, 9L, "数量填错")).thenReturn(1);

        service.voidReport(81L, new ReportService.VoidReportRequest(" 数量填错 "), 9L);
        verify(mapper).voidReport(81L, 9L, "数量填错");
        verify(audit).insert(9L, "REPORT_VOID", 81L, "作废报工 RP，原因：数量填错");

        when(mapper.lockWorkOrder(10L)).thenReturn(new ReportMapper.LockedWorkOrder(10L, "WO-10", 50L, "COMPLETED"));
        assertThatThrownBy(() -> service.voidReport(81L, new ReportService.VoidReportRequest("原因"), 9L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("终态");
    }

    @Test
    void enforcesReporterScopeAndRejectsInvalidDateRange() {
        var query = new com.gongqing.mes.common.api.PageQuery();
        service.mine(query, reportDate, reportDate.plusDays(1), 4L, 10L, "VALID", 7L);
        verify(mapper).findPage(10L, 4L, 7L, reportDate, reportDate.plusDays(1), "VALID", 20L, 0L);
        assertThatThrownBy(() -> service.search(query, reportDate, reportDate.minusDays(1), null, null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    private ReportService.SubmitReportRequest request(String key, Long workOrderId, Long productionQty,
            Long goodQty, Long unqualifiedQty) {
        return new ReportService.SubmitReportRequest(key, workOrderId, reportDate, productionQty, goodQty,
                unqualifiedQty, null);
    }

    private ReportMapper.ReportView view(ReportService.SubmitReportRequest request, Long reporterId) {
        return new ReportMapper.ReportView(81L, "RP", request.workOrderId(), "WO-10", "PL-10",
                "PO-10", 5L, "P-5", "Product", "件", reporterId, "Operator", request.reportDate(),
                request.goodQty(), request.unqualifiedQty(), request.productionQty(), "VALID", null,
                LocalDateTime.now(), null, null, null, null);
    }
}
