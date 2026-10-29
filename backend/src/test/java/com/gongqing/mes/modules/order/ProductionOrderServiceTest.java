package com.gongqing.mes.modules.order;

import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.api.PageQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductionOrderServiceTest {
    private final ProductionOrderMapper mapper = mock(ProductionOrderMapper.class);
    private final OrderAuditMapper audit = mock(OrderAuditMapper.class);
    private final ProductionOrderService service = new ProductionOrderService(mapper, audit);

    @Test
    void rejectsInactiveProductAndDoesNotInsertOrder() {
        when(mapper.findActiveProduct(8L)).thenReturn(null);
        var request = new ProductionOrderService.OrderRequest("MO-1", 8L, 2L, null, null);

        assertThatThrownBy(() -> service.create(request, 4L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void successfulCreateUsesServerActorAndWritesAudit() {
        when(mapper.findActiveProduct(8L)).thenReturn(8L);
        doAnswer(invocation -> {
            ProductionOrderMapper.OrderRecord record = invocation.getArgument(0);
            record.setId(21L);
            assertThat(record.getCreatedBy()).isEqualTo(4L);
            return 1;
        }).when(mapper).insert(any());

        Long id = service.create(new ProductionOrderService.OrderRequest(" MO-1 ", 8L, 2L,
                LocalDate.parse("2026-11-01"), " note "), 4L);

        assertThat(id).isEqualTo(21L);
        verify(audit).insert(4L, "ORDER_CREATE", 21L, "创建订单 MO-1");
    }

    @Test
    void updateRejectsStaleTimestampAndLocksExecutionStatus() {
        LocalDateTime updatedAt = LocalDateTime.parse("2026-10-22T09:00:00");
        when(mapper.findForUpdate(21L)).thenReturn(new ProductionOrderMapper.LockedOrder(21L, 8L, null, "DRAFT", updatedAt));
        when(mapper.findActiveProduct(8L)).thenReturn(8L);
        when(mapper.updateEditable(any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(21L, new ProductionOrderService.UpdateOrderRequest(
                "MO-1", 8L, 2L, null, null, updatedAt.minusSeconds(1)), 4L))
                .isInstanceOf(BusinessException.class);
        verify(audit, never()).insert(any(), anyString(), any(), anyString());
    }

    @Test
    void onlyDraftCanBeConfirmedAndExecutionOrdersCannotBeCancelled() {
        when(mapper.findForUpdate(21L))
                .thenReturn(new ProductionOrderMapper.LockedOrder(21L, 8L, null, "CONFIRMED", LocalDateTime.now()))
                .thenReturn(new ProductionOrderMapper.LockedOrder(21L, 8L, null, "IN_PROGRESS", LocalDateTime.now()));

        assertThatThrownBy(() -> service.confirm(21L, 4L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.cancel(21L, 4L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).updateStatus(any(), anyString(), any(), any());
    }

    @Test
    void cancellationIsBlockedAfterPublishedPlanOrValidReport() {
        when(mapper.findForUpdate(21L)).thenReturn(new ProductionOrderMapper.LockedOrder(
                21L, 8L, null, "CONFIRMED", LocalDateTime.now()));
        when(mapper.countExecutionPlans(21L)).thenReturn(1L);

        assertThatThrownBy(() -> service.cancel(21L, 4L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).updateStatus(any(), anyString(), any(), any());
    }

    @Test
    void cancellingConfirmedOrderCancelsItsDraftPlanAndPendingWorkOrders() {
        LocalDateTime updatedAt = LocalDateTime.now();
        when(mapper.findForUpdate(21L)).thenReturn(new ProductionOrderMapper.LockedOrder(21L, 8L, null,
                "CONFIRMED", updatedAt));
        when(mapper.updateStatus(21L, "CANCELLED", 4L, updatedAt)).thenReturn(1);

        service.cancel(21L, 4L);

        verify(mapper).cancelPendingWorkOrders(21L, 4L);
        verify(mapper).cancelDraftPlans(21L, 4L);
        verify(audit).insert(4L, "ORDER_CANCELLED", 21L, "取消订单 21");
    }

    @Test
    void executionDetailIsReturnedForExistingOrderAndRejectedWhenMissing() {
        var execution = new ProductionOrderMapper.OrderExecutionView(21L, 31L, "PL-1", "IN_PROGRESS", 100L,
                LocalDate.parse("2026-10-29"), LocalDate.parse("2026-11-30"), 41L, "WO-1", "IN_PROGRESS", 100L,
                2L, 95L, 5L, 100L);
        when(mapper.findById(21L)).thenReturn(mock(ProductionOrderMapper.OrderView.class));
        when(mapper.findExecution(21L)).thenReturn(execution);

        assertThat(service.execution(21L)).isEqualTo(execution);

        when(mapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.execution(99L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsReversedDueDateFilterBeforeQuery() {
        assertThatThrownBy(() -> service.list(new PageQuery(), null, null, null,
                LocalDate.parse("2026-10-23"), LocalDate.parse("2026-10-22")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }
}
