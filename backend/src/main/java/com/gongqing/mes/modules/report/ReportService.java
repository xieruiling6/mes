package com.gongqing.mes.modules.report;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ReportService {
    private final ReportMapper mapper;
    private final ReportAuditMapper audit;

    public ReportService(ReportMapper mapper, ReportAuditMapper audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public java.util.List<ReportMapper.AvailableWorkOrder> availableWorkOrders() {
        return mapper.findAvailableWorkOrders();
    }

    public FilterOptions filterOptions(Long reporterId, boolean includeReporters) {
        return new FilterOptions(mapper.findProductOptions(reporterId),
                includeReporters ? mapper.findReporterOptions() : java.util.List.of());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportMapper.ReportView submit(SubmitReportRequest request, Long reporterId) {
        validateQuantities(request);
        String remark = normalize(request.remark());
        var previous = mapper.findByRequestKey(request.requestKey());
        if (previous != null) return idempotentResult(previous, request, reporterId, remark);

        var workOrder = mapper.lockWorkOrder(request.workOrderId());
        if (workOrder == null) throw notFound("生产工单不存在");
        previous = mapper.findByRequestKey(request.requestKey());
        if (previous != null) return idempotentResult(previous, request, reporterId, remark);
        if (!"IN_PROGRESS".equals(workOrder.status())) throw conflict("只有生产中的工单可以报工");
        long currentQty = mapper.sumValidQty(workOrder.id());
        long newQty;
        try {
            newQty = Math.addExact(currentQty, request.productionQty());
        } catch (ArithmeticException exception) {
            throw conflict("累计报工数量超出允许范围");
        }
        if (newQty > workOrder.plannedQty()) throw conflict("本次报工会超过工单计划生产数量");

        var record = new ReportMapper.ReportRecord();
        record.setReportNo(nextReportNo());
        record.setRequestKey(request.requestKey());
        record.setWorkOrderId(workOrder.id());
        record.setReporterId(reporterId);
        record.setReportDate(request.reportDate());
        record.setGoodQty(request.goodQty());
        record.setUnqualifiedQty(request.unqualifiedQty());
        record.setRemark(remark);
        try {
            if (mapper.insert(record) != 1) throw new BusinessException(ErrorCode.DATABASE_ERROR, "提交报工失败");
        } catch (DuplicateKeyException exception) {
            var duplicate = mapper.findByRequestKey(request.requestKey());
            if (duplicate != null) return idempotentResult(duplicate, request, reporterId, remark);
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "请求键或报工编号冲突，请重新提交");
        }
        audit.insert(reporterId, "REPORT_CREATE", record.getId(), "提交报工 " + record.getReportNo() +
                "，工单 " + workOrder.workOrderNo());
        return mapper.findByRequestKey(request.requestKey());
    }

    public PageResult<ReportMapper.ReportView> mine(PageQuery page, LocalDate dateFrom, LocalDate dateTo,
            Long productId, Long workOrderId, String reportStatus, Long reporterId) {
        return page(page, workOrderId, productId, reporterId, dateFrom, dateTo, reportStatus);
    }

    public PageResult<ReportMapper.ReportView> search(PageQuery page, LocalDate dateFrom, LocalDate dateTo,
            Long productId, Long workOrderId, Long reporterId, String reportStatus) {
        return page(page, workOrderId, productId, reporterId, dateFrom, dateTo, reportStatus);
    }

    public ReportMapper.WorkOrderSummary workOrderSummary(Long workOrderId) {
        var summary = mapper.summarizeWorkOrder(workOrderId);
        if (summary == null) throw notFound("生产工单不存在");
        return summary;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void voidReport(Long id, VoidReportRequest request, Long actorId) {
        var existing = mapper.lockReport(id);
        if (existing == null) throw notFound("报工记录不存在");
        var workOrder = mapper.lockWorkOrder(existing.workOrderId());
        if (workOrder == null) throw notFound("生产工单不存在");
        if (!"IN_PROGRESS".equals(workOrder.status())) {
            throw conflict("只有生产中的工单可以更正报工；终态工单的历史记录不能作废");
        }
        if (!"VALID".equals(existing.reportStatus())) throw conflict("该报工记录已作废");
        if (mapper.voidReport(id, actorId, request.reason().trim()) != 1) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        audit.insert(actorId, "REPORT_VOID", id, "作废报工 " + existing.reportNo() + "，原因：" + request.reason().trim());
    }

    private PageResult<ReportMapper.ReportView> page(PageQuery page, Long workOrderId, Long productId, Long reporterId,
            LocalDate dateFrom, LocalDate dateTo, String reportStatus) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }
        if (reportStatus != null && !reportStatus.matches("VALID|VOID")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报工状态不合法");
        }
        final long offset;
        try {
            offset = Math.multiplyExact(page.getPage() - 1, page.getSize());
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分页参数超出范围");
        }
        var records = mapper.findPage(workOrderId, productId, reporterId, dateFrom, dateTo, reportStatus,
                page.getSize(), offset);
        return new PageResult<>(records, page.getPage(), page.getSize(),
                mapper.countPage(workOrderId, productId, reporterId, dateFrom, dateTo, reportStatus));
    }

    private ReportMapper.ReportView idempotentResult(ReportMapper.ReportView previous, SubmitReportRequest request,
            Long reporterId, String remark) {
        if (!previous.workOrderId().equals(request.workOrderId()) || !previous.reporterId().equals(reporterId) ||
                !previous.reportDate().equals(request.reportDate()) || !previous.goodQty().equals(request.goodQty()) ||
                !previous.unqualifiedQty().equals(request.unqualifiedQty()) || !java.util.Objects.equals(previous.remark(), remark)) {
            throw conflict("该请求键已用于其他报工数据");
        }
        return previous;
    }

    private void validateQuantities(SubmitReportRequest request) {
        if (request.goodQty() < 0 || request.unqualifiedQty() < 0 ||
                request.goodQty() == 0 && request.unqualifiedQty() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "合格数和不合格数不能为负，且至少一项必须大于零");
        }
        final long total;
        try {
            total = Math.addExact(request.goodQty(), request.unqualifiedQty());
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报工数量超出允许范围");
        }
        if (total != request.productionQty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "生产数量必须等于合格数与不合格数之和");
        }
    }

    private String nextReportNo() {
        return "RP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")) +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private BusinessException conflict(String message) { return new BusinessException(ErrorCode.BUSINESS_CONFLICT, message); }
    private BusinessException notFound(String message) { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message); }

    public record SubmitReportRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
                    regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") String requestKey,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long workOrderId,
            @jakarta.validation.constraints.NotNull LocalDate reportDate,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long productionQty,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long goodQty,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero Long unqualifiedQty,
            @jakarta.validation.constraints.Size(max = 500) String remark) { }

    public record VoidReportRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String reason) { }
    public record FilterOptions(java.util.List<ReportMapper.ProductOption> products,
            java.util.List<ReportMapper.ReporterOption> reporters) { }
}
