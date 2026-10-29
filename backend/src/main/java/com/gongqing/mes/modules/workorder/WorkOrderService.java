package com.gongqing.mes.modules.workorder;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class WorkOrderService {
    private final WorkOrderMapper mapper;
    private final WorkOrderAuditMapper audit;

    public WorkOrderService(WorkOrderMapper mapper, WorkOrderAuditMapper audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public List<WorkOrderMapper.AvailablePlan> availablePlans() { return mapper.findAvailablePlans(); }

    public PageResult<WorkOrderMapper.WorkOrderView> list(PageQuery page, String workOrderNo, Long productId,
            String status, LocalDate dateFrom, LocalDate dateTo) {
        validateStatus(status);
        validateDateRange(dateFrom, dateTo);
        String normalizedNo = normalize(workOrderNo);
        long offset = (page.getPage() - 1) * page.getSize();
        var records = mapper.findPage(normalizedNo, productId, status, dateFrom, dateTo, page.getSize(), offset);
        var total = mapper.countPage(normalizedNo, productId, status, dateFrom, dateTo);
        return new PageResult<>(records, page.getPage(), page.getSize(), total);
    }

    public WorkOrderMapper.WorkOrderView detail(Long id) {
        var result = mapper.findById(id);
        if (result == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产工单不存在");
        return result;
    }

    public PageResult<WorkOrderMapper.ReportView> reports(Long id, PageQuery page) {
        requireWorkOrder(id);
        long offset = (page.getPage() - 1) * page.getSize();
        return new PageResult<>(mapper.findReports(id, page.getSize(), offset), page.getPage(), page.getSize(),
                mapper.countReports(id));
    }

    @Transactional
    public Long createFromPlan(Long planId, CreateFromPlanRequest request, Long actorId) {
        Long orderId = mapper.findOrderIdByPlan(planId);
        if (orderId == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产计划不存在");
        var order = mapper.lockOrder(orderId);
        var plan = mapper.lockPlan(planId);
        if (order == null || plan == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联订单或计划不存在");
        if (!"RELEASED".equals(plan.status()) || !"CONFIRMED".equals(order.status())) {
            throw conflict("只有已发布且关联订单仍有效的计划可以创建工单");
        }
        if (!plan.planQty().equals(order.orderQty())) throw conflict("计划数量与生产订单数量不一致");
        if (mapper.countByPlan(planId) > 0) throw conflict("该计划已创建工单");
        var record = new WorkOrderMapper.WorkOrderRecord();
        record.setWorkOrderNo(nextWorkOrderNo());
        record.setPlanId(planId);
        record.setPlannedQty(plan.planQty());
        record.setStartDate(plan.startDate());
        record.setEndDate(plan.endDate());
        record.setRemark(normalize(request.remark()));
        record.setCreatedBy(actorId);
        if (mapper.insert(record) != 1) throw new BusinessException(ErrorCode.DATABASE_ERROR, "创建生产工单失败");
        audit.insert(actorId, "WORK_ORDER_CREATE", record.getId(), "从计划 " + planId + " 创建工单 " + record.getWorkOrderNo());
        return record.getId();
    }

    @Transactional
    public void dispatch(Long id, StatusRequest request, Long actorId) {
        transition(id, request.expectedUpdatedAt(), "PENDING", "RELEASED", "WORK_ORDER_DISPATCH", "下发生产工单", actorId);
    }

    @Transactional
    public void start(Long id, StatusRequest request, Long actorId) {
        WorkContext context = lockContext(id);
        requireState(context.workOrder(), "RELEASED", "只有已下发工单可以开工");
        checkVersion(context.workOrder(), request.expectedUpdatedAt());
        requireState(context.plan(), "RELEASED", "关联计划状态不允许开工");
        requireState(context.order(), "CONFIRMED", "关联订单状态不允许开工");
        transitionRow(context.workOrder(), "RELEASED", "IN_PROGRESS", request.expectedUpdatedAt(), actorId);
        if (mapper.markPlanInProgress(context.plan().id(), actorId) != 1 ||
                mapper.markOrderInProgress(context.order().id(), actorId) != 1) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
        audit.insert(actorId, "WORK_ORDER_START", id, "开始生产工单 " + context.workOrder().workOrderNo());
    }

    @Transactional
    public void pause(Long id, StatusRequest request, Long actorId) {
        transition(id, request.expectedUpdatedAt(), "IN_PROGRESS", "PAUSED", "WORK_ORDER_PAUSE", "暂停生产工单", actorId);
    }

    @Transactional
    public void resume(Long id, StatusRequest request, Long actorId) {
        transition(id, request.expectedUpdatedAt(), "PAUSED", "IN_PROGRESS", "WORK_ORDER_RESUME", "恢复生产工单", actorId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void complete(Long id, StatusRequest request, Long actorId) {
        WorkContext context = lockContext(id);
        requireState(context.workOrder(), "IN_PROGRESS", "只有生产中的工单可以完成");
        checkVersion(context.workOrder(), request.expectedUpdatedAt());
        long reportedQty = mapper.sumValidReportedQty(id);
        if (reportedQty != context.workOrder().plannedQty()) {
            throw conflict("有效报工总数必须恰好等于工单计划数量后才能完成");
        }
        transitionRow(context.workOrder(), "IN_PROGRESS", "COMPLETED", request.expectedUpdatedAt(), actorId);
        if (mapper.markPlanCompleted(context.plan().id(), actorId) != 1 ||
                mapper.markOrderCompleted(context.order().id(), actorId) != 1) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
        audit.insert(actorId, "WORK_ORDER_COMPLETE", id, "完成生产工单 " + context.workOrder().workOrderNo());
    }

    private void transition(Long id, LocalDateTime expectedUpdatedAt, String source, String target,
                            String operation, String detail, Long actorId) {
        var workOrder = lockContext(id).workOrder();
        requireState(workOrder, source, "当前工单状态不允许此操作");
        checkVersion(workOrder, expectedUpdatedAt);
        transitionRow(workOrder, source, target, expectedUpdatedAt, actorId);
        audit.insert(actorId, operation, id, detail + " " + workOrder.workOrderNo());
    }

    private void transitionRow(WorkOrderMapper.LockedWorkOrder workOrder, String source, String target,
                               LocalDateTime expectedUpdatedAt, Long actorId) {
        if (mapper.transition(workOrder.id(), source, target, actorId, expectedUpdatedAt) != 1) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
    }

    private WorkContext lockContext(Long id) {
        Long planId = mapper.findPlanIdByWorkOrder(id);
        if (planId == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产工单不存在");
        Long orderId = mapper.findOrderIdByPlan(planId);
        if (orderId == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联生产计划不存在");
        var order = mapper.lockOrder(orderId);
        var plan = mapper.lockPlan(planId);
        var workOrder = mapper.lockWorkOrder(id);
        if (order == null || plan == null || workOrder == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产工单或关联数据不存在");
        }
        return new WorkContext(order, plan, workOrder);
    }

    private WorkOrderMapper.WorkOrderView requireWorkOrder(Long id) {
        var workOrder = mapper.findById(id);
        if (workOrder == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产工单不存在");
        return workOrder;
    }

    private void requireState(WorkOrderMapper.LockedWorkOrder entity, String expected, String message) {
        if (!expected.equals(entity.status())) throw conflict(message);
    }

    private void requireState(WorkOrderMapper.LockedPlan entity, String expected, String message) {
        if (!expected.equals(entity.status())) throw conflict(message);
    }

    private void requireState(WorkOrderMapper.LockedOrder entity, String expected, String message) {
        if (!expected.equals(entity.status())) throw conflict(message);
    }

    private void checkVersion(WorkOrderMapper.LockedWorkOrder workOrder, LocalDateTime expectedUpdatedAt) {
        if (!workOrder.updatedAt().equals(expectedUpdatedAt)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private String nextWorkOrderNo() {
        return "WO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")) +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private void validateStatus(String status) {
        if (status != null && !status.matches("PENDING|RELEASED|IN_PROGRESS|PAUSED|COMPLETED|CANCELLED")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单状态不合法");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划日期结束日期不能早于开始日期");
        }
    }

    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.BUSINESS_CONFLICT, message); }

    private record WorkContext(WorkOrderMapper.LockedOrder order, WorkOrderMapper.LockedPlan plan,
                               WorkOrderMapper.LockedWorkOrder workOrder) { }

    public record CreateFromPlanRequest(@jakarta.validation.constraints.Size(max = 500) String remark) { }
    public record StatusRequest(@jakarta.validation.constraints.NotNull LocalDateTime expectedUpdatedAt) { }
}
