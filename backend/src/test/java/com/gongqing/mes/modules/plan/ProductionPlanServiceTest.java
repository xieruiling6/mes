package com.gongqing.mes.modules.plan;

import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionPlanServiceTest {
    private final ProductionPlanMapper mapper = mock(ProductionPlanMapper.class);
    private final PlanAuditMapper audit = mock(PlanAuditMapper.class);
    private final ProductionPlanService service = new ProductionPlanService(mapper, audit);
    private final LocalDate start = LocalDate.parse("2026-11-01");
    private final LocalDate end = LocalDate.parse("2026-11-05");

    @Test
    void createRequiresConfirmedOrderAndExactOrderQuantity() {
        when(mapper.lockOrder(10L)).thenReturn(new ProductionPlanMapper.LockedOrder(
                10L, "MO-10", 100L, LocalDate.parse("2026-11-10"), "CONFIRMED"));
        assertThatThrownBy(() -> service.create(new ProductionPlanService.CreatePlanRequest(10L, 99L,
                start, end, 1, null), 2L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void createPersistsFullQuantityAndAuditsActor() {
        when(mapper.lockOrder(10L)).thenReturn(new ProductionPlanMapper.LockedOrder(
                10L, "MO-10", 100L, LocalDate.parse("2026-11-10"), "CONFIRMED"));
        doAnswer(invocation -> {
            ProductionPlanMapper.PlanRecord record = invocation.getArgument(0);
            assertThat(record.getPlanQty()).isEqualTo(100L);
            assertThat(record.getCreatedBy()).isEqualTo(2L);
            record.setId(30L);
            return 1;
        }).when(mapper).insert(any());

        assertThat(service.create(new ProductionPlanService.CreatePlanRequest(10L, 100L,
                start, end, 2, "line A"), 2L)).isEqualTo(30L);
        verify(audit).insert(eq(2L), eq("PLAN_CREATE"), eq(30L), argThat(value -> value.startsWith("创建生产计划 PL")));
    }

    @Test
    void createRejectsPlanEndingAfterOrderDueDate() {
        when(mapper.lockOrder(10L)).thenReturn(new ProductionPlanMapper.LockedOrder(
                10L, "MO-10", 100L, LocalDate.parse("2026-11-04"), "CONFIRMED"));
        assertThatThrownBy(() -> service.create(new ProductionPlanService.CreatePlanRequest(10L, 100L,
                start, end, 0, null), 2L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void releaseChangesPlanStateWithoutCreatingWorkOrder() {
        LocalDateTime version = LocalDateTime.parse("2026-10-22T10:00:00.123");
        when(mapper.findOrderIdByPlan(30L)).thenReturn(10L);
        when(mapper.lockOrder(10L)).thenReturn(new ProductionPlanMapper.LockedOrder(
                10L, "MO-10", 100L, LocalDate.parse("2026-11-10"), "CONFIRMED"));
        when(mapper.lockPlan(30L)).thenReturn(new ProductionPlanMapper.LockedPlan(30L, 10L, "DRAFT", version));
        when(mapper.findById(30L)).thenReturn(new ProductionPlanMapper.PlanView(30L, "PL-30", 10L,
                "MO-10", 100L, LocalDate.parse("2026-11-10"), 1L, "P-1", "Part", null, "件", 100L,
                start, end, 1, "DRAFT", null, 2L, "Planner", version, null, version, null, null,
                0L, 0L, null, null, null, 0L, 0L, 0L, 0L));
        when(mapper.release(30L, 2L, version)).thenReturn(1);

        service.release(30L, version, 2L);

        verify(audit).insert(2L, "PLAN_RELEASE", 30L, "发布生产计划 30");
    }

    @Test
    void cancelRejectsStaleVersionAndExistingExecution() {
        LocalDateTime version = LocalDateTime.parse("2026-10-22T10:00:00.123");
        when(mapper.findOrderIdByPlan(30L)).thenReturn(10L);
        when(mapper.lockOrder(10L)).thenReturn(new ProductionPlanMapper.LockedOrder(
                10L, "MO-10", 100L, LocalDate.parse("2026-11-10"), "CONFIRMED"));
        when(mapper.lockPlan(30L)).thenReturn(new ProductionPlanMapper.LockedPlan(30L, 10L, "RELEASED", version));
        when(mapper.countStartedWorkOrders(30L)).thenReturn(0L);
        when(mapper.countValidReports(30L)).thenReturn(1L);

        assertThatThrownBy(() -> service.cancel(30L, version.minusSeconds(1), 2L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.cancel(30L, version, 2L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).cancel(any(), any(), any());
    }

    @Test
    void onlyDraftPlanCanBeEdited() {
        when(mapper.findOrderIdByPlan(30L)).thenReturn(10L);
        when(mapper.lockOrder(10L)).thenReturn(new ProductionPlanMapper.LockedOrder(
                10L, "MO-10", 100L, LocalDate.parse("2026-11-10"), "CONFIRMED"));
        when(mapper.lockPlan(30L)).thenReturn(new ProductionPlanMapper.LockedPlan(
                30L, 10L, "RELEASED", LocalDateTime.now()));
        assertThatThrownBy(() -> service.update(30L, new ProductionPlanService.UpdatePlanRequest(
                100L, start, end, 0, null, LocalDateTime.now()), 2L)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).updateDraft(any());
    }
}
