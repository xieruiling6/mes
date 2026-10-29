package com.gongqing.mes.modules.plan;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ProductionPlanService {
    private final ProductionPlanMapper mapper;
    private final PlanAuditMapper audit;

    public ProductionPlanService(ProductionPlanMapper mapper, PlanAuditMapper audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public java.util.List<ProductionPlanMapper.AvailableOrder> availableOrders() { return mapper.findAvailableOrders(); }

    public PageResult<ProductionPlanMapper.PlanView> list(PageQuery page, String planNo, String orderNo,
            Long productId, String status, Integer priority, LocalDate startDateFrom, LocalDate startDateTo,
            LocalDate endDateFrom, LocalDate endDateTo) {
        validateStatus(status);
        validateRange(startDateFrom, startDateTo, "计划开始日期");
        validateRange(endDateFrom, endDateTo, "计划完成日期");
        validatePriority(priority);
        String normalizedPlanNo = normalize(planNo);
        String normalizedOrderNo = normalize(orderNo);
        long offset = (page.getPage() - 1) * page.getSize();
        var records = mapper.findPage(normalizedPlanNo, normalizedOrderNo, productId, status, priority,
                startDateFrom, startDateTo, endDateFrom, endDateTo, page.getSize(), offset);
        long total = mapper.countPage(normalizedPlanNo, normalizedOrderNo, productId, status, priority,
                startDateFrom, startDateTo, endDateFrom, endDateTo);
        return new PageResult<>(records, page.getPage(), page.getSize(), total);
    }

    public ProductionPlanMapper.PlanView detail(Long id) {
        var plan = mapper.findById(id);
        if (plan == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产计划不存在");
        return plan;
    }

    @Transactional
    public Long create(CreatePlanRequest request, Long actorId) {
        var order = mapper.lockOrder(request.productionOrderId());
        if (order == null || !"CONFIRMED".equals(order.status())) {
            throw conflict("只有已确认且有效的生产订单可以排产");
        }
        if (mapper.countPlansForOrder(order.id()) > 0) throw conflict("该订单已经有生产计划");
        validateOrderPlan(order, request.planQty(), request.startDate(), request.endDate(), request.priority());
        var record = from(request);
        record.setPlanNo(nextNumber("PL"));
        record.setProductionOrderId(order.id());
        record.setCreatedBy(actorId);
        mapper.insert(record);
        audit.insert(actorId, "PLAN_CREATE", record.getId(), "创建生产计划 " + record.getPlanNo());
        return record.getId();
    }

    @Transactional
    public void update(Long id, UpdatePlanRequest request, Long actorId) {
        var context = lockContext(id);
        var plan = context.plan();
        if (!"DRAFT".equals(plan.status())) throw conflict("只有草稿计划可以修改");
        if (!plan.updatedAt().equals(request.expectedUpdatedAt())) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var order = context.order();
        if (order == null || !"CONFIRMED".equals(order.status())) throw conflict("关联订单已不可排产");
        validateOrderPlan(order, request.planQty(), request.startDate(), request.endDate(), request.priority());
        var record = from(request);
        record.setId(id);
        record.setUpdatedBy(actorId);
        record.setExpectedUpdatedAt(request.expectedUpdatedAt());
        if (mapper.updateDraft(record) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        audit.insert(actorId, "PLAN_UPDATE", id, "更新生产计划 " + id);
    }

    @Transactional
    public void release(Long id, LocalDateTime expectedUpdatedAt, Long actorId) {
        var context = lockContext(id);
        var plan = context.plan();
        if (!"DRAFT".equals(plan.status())) throw conflict("只有草稿计划可以发布");
        checkVersion(plan, expectedUpdatedAt);
        var order = context.order();
        if (order == null || !"CONFIRMED".equals(order.status())) throw conflict("关联订单状态已变化，不能发布计划");
        if (mapper.release(id, actorId, expectedUpdatedAt) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        audit.insert(actorId, "PLAN_RELEASE", id, "发布生产计划 " + id);
    }

    @Transactional
    public void cancel(Long id, LocalDateTime expectedUpdatedAt, Long actorId) {
        var plan = lockContext(id).plan();
        if (!"DRAFT".equals(plan.status()) && !"RELEASED".equals(plan.status())) {
            throw conflict("只有未执行计划可以取消");
        }
        checkVersion(plan, expectedUpdatedAt);
        if (mapper.countStartedWorkOrders(id) > 0 || mapper.countValidReports(id) > 0) {
            throw conflict("关联工单已开始或存在有效报工，不能取消计划");
        }
        mapper.cancelPendingWorkOrders(id, actorId);
        if (mapper.cancel(id, actorId, expectedUpdatedAt) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        audit.insert(actorId, "PLAN_CANCEL", id, "取消生产计划 " + id);
    }

    private ProductionPlanMapper.LockedPlan lockPlan(Long id) {
        var plan = mapper.lockPlan(id);
        if (plan == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产计划不存在");
        return plan;
    }

    private PlanContext lockContext(Long id) {
        Long orderId = mapper.findOrderIdByPlan(id);
        if (orderId == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产计划不存在");
        var order = mapper.lockOrder(orderId);
        if (order == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联生产订单不存在");
        return new PlanContext(lockPlan(id), order);
    }

    private void checkVersion(ProductionPlanMapper.LockedPlan plan, LocalDateTime expected) {
        if (!plan.updatedAt().equals(expected)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
    }

    private void validateOrderPlan(ProductionPlanMapper.LockedOrder order, Long quantity, LocalDate start,
                                   LocalDate end, Integer priority) {
        if (!order.orderQty().equals(quantity)) throw conflict("计划数量必须等于订单数量");
        validateDates(start, end, order.dueDate());
        validatePriority(priority);
    }

    private void validateDates(LocalDate start, LocalDate end, LocalDate dueDate) {
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划完成日期不能早于计划开始日期");
        }
        if (dueDate != null && end.isAfter(dueDate)) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "计划完成日期不能晚于订单交期");
        }
    }

    private void validatePriority(Integer priority) {
        if (priority != null && (priority < 0 || priority > Short.MAX_VALUE)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优先级必须在 0 到 32767 之间");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !status.matches("DRAFT|RELEASED|IN_PROGRESS|COMPLETED|CANCELLED")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "计划状态不合法");
        }
    }

    private void validateRange(LocalDate from, LocalDate to, String label) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + "结束日期不能早于开始日期");
        }
    }

    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }

    private ProductionPlanMapper.PlanRecord from(PlanFields request) {
        var record = new ProductionPlanMapper.PlanRecord();
        record.setPlanQty(request.planQty());
        record.setStartDate(request.startDate());
        record.setEndDate(request.endDate());
        record.setPriority(request.priority() == null ? 0 : request.priority());
        record.setRemark(normalize(request.remark()));
        return record;
    }

    private String nextNumber(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")) +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.BUSINESS_CONFLICT, message); }

    private record PlanContext(ProductionPlanMapper.LockedPlan plan, ProductionPlanMapper.LockedOrder order) { }

    public interface PlanFields { Long planQty(); LocalDate startDate(); LocalDate endDate(); Integer priority(); String remark(); }
    public record CreatePlanRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long productionOrderId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long planQty,
            @jakarta.validation.constraints.NotNull LocalDate startDate, @jakarta.validation.constraints.NotNull LocalDate endDate,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(32767) Integer priority,
            @jakarta.validation.constraints.Size(max = 500) String remark) implements PlanFields { }
    public record UpdatePlanRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long planQty,
            @jakarta.validation.constraints.NotNull LocalDate startDate, @jakarta.validation.constraints.NotNull LocalDate endDate,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(32767) Integer priority,
            @jakarta.validation.constraints.Size(max = 500) String remark,
            @jakarta.validation.constraints.NotNull LocalDateTime expectedUpdatedAt) implements PlanFields { }
}
