package com.gongqing.mes.modules.workorder;

import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkOrderServiceTest {
    private final WorkOrderMapper mapper = mock(WorkOrderMapper.class);
    private final WorkOrderAuditMapper audit = mock(WorkOrderAuditMapper.class);
    private final WorkOrderService service = new WorkOrderService(mapper, audit);
    private final LocalDateTime v1 = LocalDateTime.parse("2026-10-22T09:00:00.123");
    private final LocalDateTime v2 = v1.plusSeconds(1);
    private final LocalDateTime v3 = v2.plusSeconds(1);
    private final LocalDateTime v4 = v3.plusSeconds(1);
    private final LocalDateTime v5 = v4.plusSeconds(1);

    @Test
    void createsWorkOrderFromReleasedPlanUsingPlanQuantityAndAuditsIt() {
        when(mapper.findOrderIdByPlan(30L)).thenReturn(10L);
        when(mapper.lockOrder(10L)).thenReturn(new WorkOrderMapper.LockedOrder(10L, 100L, "CONFIRMED"));
        when(mapper.lockPlan(30L)).thenReturn(new WorkOrderMapper.LockedPlan(30L, 10L, 100L,
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-05"), "RELEASED"));
        when(mapper.countByPlan(30L)).thenReturn(0L);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            WorkOrderMapper.WorkOrderRecord record = invocation.getArgument(0);
            record.setId(40L);
            assertThat(record.getPlannedQty()).isEqualTo(100L);
            assertThat(record.getCreatedBy()).isEqualTo(7L);
            assertThat(record.getWorkOrderNo()).startsWith("WO");
            return 1;
        });

        assertThat(service.createFromPlan(30L, new WorkOrderService.CreateFromPlanRequest("  note  "), 7L)).isEqualTo(40L);

        verify(audit).insert(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("WORK_ORDER_CREATE"),
                org.mockito.ArgumentMatchers.eq(40L), org.mockito.ArgumentMatchers.contains("从计划 30 创建工单"));
    }

    @Test
    void rejectsUnreleasedPlanQuantityMismatchAndDuplicateCreation() {
        when(mapper.findOrderIdByPlan(30L)).thenReturn(10L);
        when(mapper.lockOrder(10L)).thenReturn(new WorkOrderMapper.LockedOrder(10L, 100L, "CONFIRMED"));
        when(mapper.lockPlan(30L)).thenReturn(new WorkOrderMapper.LockedPlan(30L, 10L, 99L,
                LocalDate.now(), LocalDate.now(), "DRAFT"));
        assertThatThrownBy(() -> service.createFromPlan(30L, new WorkOrderService.CreateFromPlanRequest(null), 7L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());

        when(mapper.lockPlan(30L)).thenReturn(new WorkOrderMapper.LockedPlan(30L, 10L, 99L,
                LocalDate.now(), LocalDate.now(), "RELEASED"));
        assertThatThrownBy(() -> service.createFromPlan(30L, new WorkOrderService.CreateFromPlanRequest(null), 7L))
                .isInstanceOf(BusinessException.class);
        when(mapper.lockPlan(30L)).thenReturn(new WorkOrderMapper.LockedPlan(30L, 10L, 100L,
                LocalDate.now(), LocalDate.now(), "RELEASED"));
        when(mapper.countByPlan(30L)).thenReturn(1L);
        assertThatThrownBy(() -> service.createFromPlan(30L, new WorkOrderService.CreateFromPlanRequest(null), 7L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void followsTheCompleteValidLifecycleAndSynchronizesOrderAndPlan() {
        stubContext("PENDING", v1, "RELEASED", "CONFIRMED");
        when(mapper.transition(40L, "PENDING", "RELEASED", 7L, v1)).thenReturn(1);
        service.dispatch(40L, new WorkOrderService.StatusRequest(v1), 7L);

        stubContext("RELEASED", v2, "RELEASED", "CONFIRMED");
        when(mapper.transition(40L, "RELEASED", "IN_PROGRESS", 7L, v2)).thenReturn(1);
        when(mapper.markPlanInProgress(30L, 7L)).thenReturn(1);
        when(mapper.markOrderInProgress(10L, 7L)).thenReturn(1);
        service.start(40L, new WorkOrderService.StatusRequest(v2), 7L);

        stubContext("IN_PROGRESS", v3, "IN_PROGRESS", "IN_PROGRESS");
        when(mapper.transition(40L, "IN_PROGRESS", "PAUSED", 7L, v3)).thenReturn(1);
        service.pause(40L, new WorkOrderService.StatusRequest(v3), 7L);

        stubContext("PAUSED", v4, "IN_PROGRESS", "IN_PROGRESS");
        when(mapper.transition(40L, "PAUSED", "IN_PROGRESS", 7L, v4)).thenReturn(1);
        service.resume(40L, new WorkOrderService.StatusRequest(v4), 7L);

        stubContext("IN_PROGRESS", v5, "IN_PROGRESS", "IN_PROGRESS");
        when(mapper.sumValidReportedQty(40L)).thenReturn(100L);
        when(mapper.transition(40L, "IN_PROGRESS", "COMPLETED", 7L, v5)).thenReturn(1);
        when(mapper.markPlanCompleted(30L, 7L)).thenReturn(1);
        when(mapper.markOrderCompleted(10L, 7L)).thenReturn(1);
        service.complete(40L, new WorkOrderService.StatusRequest(v5), 7L);

        verify(mapper).markPlanCompleted(30L, 7L);
        verify(mapper).markOrderCompleted(10L, 7L);
        verify(audit).insert(7L, "WORK_ORDER_DISPATCH", 40L, "下发生产工单 WO-40");
        verify(audit).insert(7L, "WORK_ORDER_START", 40L, "开始生产工单 WO-40");
        verify(audit).insert(7L, "WORK_ORDER_PAUSE", 40L, "暂停生产工单 WO-40");
        verify(audit).insert(7L, "WORK_ORDER_RESUME", 40L, "恢复生产工单 WO-40");
        verify(audit).insert(7L, "WORK_ORDER_COMPLETE", 40L, "完成生产工单 WO-40");
    }

    @Test
    void rejectsIllegalTransitionsTerminalRestartsAndStaleVersions() {
        stubContext("PENDING", v1, "RELEASED", "CONFIRMED");
        assertThatThrownBy(() -> service.start(40L, new WorkOrderService.StatusRequest(v1), 7L))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.dispatch(40L, new WorkOrderService.StatusRequest(v1.minusSeconds(1)), 7L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).transition(eq(40L), anyString(), anyString(), anyLong(), any());

        stubContext("COMPLETED", v2, "COMPLETED", "COMPLETED");
        assertThatThrownBy(() -> service.resume(40L, new WorkOrderService.StatusRequest(v2), 7L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).markPlanInProgress(any(), any());

        stubContext("CANCELLED", v3, "CANCELLED", "CANCELLED");
        assertThatThrownBy(() -> service.start(40L, new WorkOrderService.StatusRequest(v3), 7L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void completionRequiresExactValidReportedQuantity() {
        stubContext("IN_PROGRESS", v1, "IN_PROGRESS", "IN_PROGRESS");
        when(mapper.sumValidReportedQty(40L)).thenReturn(99L);
        assertThatThrownBy(() -> service.complete(40L, new WorkOrderService.StatusRequest(v1), 7L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).transition(any(), anyString(), anyString(), any(), any());

        when(mapper.sumValidReportedQty(40L)).thenReturn(101L);
        assertThatThrownBy(() -> service.complete(40L, new WorkOrderService.StatusRequest(v1), 7L))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).transition(any(), anyString(), anyString(), any(), any());
    }

    private void stubContext(String workStatus, LocalDateTime updatedAt, String planStatus, String orderStatus) {
        when(mapper.findPlanIdByWorkOrder(40L)).thenReturn(30L);
        when(mapper.findOrderIdByPlan(30L)).thenReturn(10L);
        when(mapper.lockOrder(10L)).thenReturn(new WorkOrderMapper.LockedOrder(10L, 100L, orderStatus));
        when(mapper.lockPlan(30L)).thenReturn(new WorkOrderMapper.LockedPlan(30L, 10L, 100L,
                LocalDate.now(), LocalDate.now(), planStatus));
        when(mapper.lockWorkOrder(40L)).thenReturn(new WorkOrderMapper.LockedWorkOrder(
                40L, "WO-40", 30L, 100L, workStatus, updatedAt));
    }
}
