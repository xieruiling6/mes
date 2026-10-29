package com.gongqing.mes.modules.order;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ProductionOrderService {
    private final ProductionOrderMapper mapper;
    private final OrderAuditMapper audit;

    public ProductionOrderService(ProductionOrderMapper mapper, OrderAuditMapper audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public PageResult<ProductionOrderMapper.OrderView> list(PageQuery page, String orderNo, Long productId,
                                                              String status, LocalDate dueDateFrom, LocalDate dueDateTo) {
        validateStatusFilter(status);
        validateDateRange(dueDateFrom, dueDateTo);
        String normalizedOrderNo = StringUtils.hasText(orderNo) ? orderNo.trim() : null;
        long offset = (page.getPage() - 1) * page.getSize();
        return new PageResult<>(mapper.findPage(normalizedOrderNo, productId, status, dueDateFrom, dueDateTo,
                page.getSize(), offset), page.getPage(), page.getSize(),
                mapper.count(normalizedOrderNo, productId, status, dueDateFrom, dueDateTo));
    }

    public ProductionOrderMapper.OrderView detail(Long id) {
        var order = mapper.findById(id);
        if (order == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产订单不存在");
        return order;
    }

    public ProductionOrderMapper.OrderExecutionView execution(Long id) {
        detail(id);
        return mapper.findExecution(id);
    }

    @Transactional
    public Long create(OrderRequest request, Long actorId) {
        requireActiveProduct(request.productId());
        validateNewDueDate(request.dueDate());
        var order = from(request);
        order.setCreatedBy(actorId);
        try {
            mapper.insert(order);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "订单编号已存在");
        }
        audit.insert(actorId, "ORDER_CREATE", order.getId(), "创建订单 " + order.getOrderNo());
        return order.getId();
    }

    @Transactional
    public void update(Long id, UpdateOrderRequest request, Long actorId) {
        var locked = lockOrder(id);
        if (!editable(locked.status())) throw conflict("当前状态不允许编辑订单");
        if (!locked.productId().equals(request.productId())) requireActiveProduct(request.productId());
        if (!java.util.Objects.equals(locked.dueDate(), request.dueDate())) validateNewDueDate(request.dueDate());
        var order = from(request);
        order.setId(id);
        order.setUpdatedBy(actorId);
        order.setExpectedUpdatedAt(request.expectedUpdatedAt());
        try {
            if (mapper.updateEditable(order) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "订单编号已存在");
        }
        audit.insert(actorId, "ORDER_UPDATE", id, "更新订单 " + order.getOrderNo());
    }

    @Transactional
    public void confirm(Long id, Long actorId) {
        var locked = lockOrder(id);
        if (!"DRAFT".equals(locked.status())) throw conflict("只有草稿订单可以确认");
        changeStatus(locked, "CONFIRMED", actorId);
        audit.insert(actorId, "ORDER_CONFIRMED", id, "确认订单 " + id);
    }

    @Transactional
    public void cancel(Long id, Long actorId) {
        var locked = lockOrder(id);
        if (!"DRAFT".equals(locked.status()) && !"CONFIRMED".equals(locked.status())) {
            throw conflict("当前状态不允许取消订单");
        }
        if (mapper.countExecutionPlans(id) > 0 || mapper.countValidReports(id) > 0) {
            throw conflict("已有已发布计划或有效报工，不能取消订单");
        }
        mapper.cancelPendingWorkOrders(id, actorId);
        mapper.cancelDraftPlans(id, actorId);
        changeStatus(locked, "CANCELLED", actorId);
        audit.insert(actorId, "ORDER_CANCELLED", id, "取消订单 " + id);
    }

    private void changeStatus(ProductionOrderMapper.LockedOrder locked, String target, Long actorId) {
        if (mapper.updateStatus(locked.id(), target, actorId, locked.updatedAt()) != 1) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
    }

    private ProductionOrderMapper.LockedOrder lockOrder(Long id) {
        var order = mapper.findForUpdate(id);
        if (order == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生产订单不存在");
        return order;
    }

    private void requireActiveProduct(Long productId) {
        if (mapper.findActiveProduct(productId) == null) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "产品不存在、已停用或已删除，不能用于新订单");
        }
    }

    private boolean editable(String status) { return "DRAFT".equals(status) || "CONFIRMED".equals(status); }

    private void validateStatusFilter(String status) {
        if (status != null && !status.matches("DRAFT|CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "订单状态不合法");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "交期结束日期不能早于开始日期");
        }
    }

    private void validateNewDueDate(LocalDate dueDate) {
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "要求交期不能早于今天");
        }
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.BUSINESS_CONFLICT, message);
    }

    private ProductionOrderMapper.OrderRecord from(OrderFields request) {
        var order = new ProductionOrderMapper.OrderRecord();
        order.setOrderNo(request.orderNo().trim());
        order.setProductId(request.productId());
        order.setOrderQty(request.orderQty());
        order.setDueDate(request.dueDate());
        order.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        return order;
    }

    public interface OrderFields {
        String orderNo(); Long productId(); Long orderQty(); LocalDate dueDate(); String remark();
    }

    public record OrderRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String orderNo,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long productId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long orderQty,
            LocalDate dueDate,
            @jakarta.validation.constraints.Size(max = 500) String remark) implements OrderFields { }

    public record UpdateOrderRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String orderNo,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long productId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long orderQty,
            LocalDate dueDate,
            @jakarta.validation.constraints.Size(max = 500) String remark,
            @jakarta.validation.constraints.NotNull LocalDateTime expectedUpdatedAt) implements OrderFields { }

}
