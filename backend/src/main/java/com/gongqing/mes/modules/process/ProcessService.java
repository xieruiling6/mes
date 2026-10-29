package com.gongqing.mes.modules.process;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProcessService {
    private final ProcessMapper mapper;
    public ProcessService(ProcessMapper mapper) { this.mapper = mapper; }

    public PageResult<ProcessMapper.ProcessView> list(PageQuery page, String keyword, String status) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        validateStatus(status);
        long offset = (page.getPage() - 1) * page.getSize();
        return new PageResult<>(mapper.findPage(normalized, status, page.getSize(), offset), page.getPage(),
                page.getSize(), mapper.count(normalized, status));
    }
    public ProcessMapper.ProcessView detail(Long id) {
        var process = mapper.findById(id);
        if (process == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工序不存在");
        return process;
    }
    public Long create(ProcessRequest request) {
        var record = from(request);
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "工序编码已存在");
        }
        return record.getId();
    }
    public void update(Long id, ProcessRequest request) {
        requireProcess(id);
        var record = from(request);
        record.setId(id);
        try {
            mapper.update(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "工序编码已存在");
        }
    }
    public void changeStatus(Long id, StatusRequest request) {
        requireProcess(id); validateStatus(request.status()); mapper.updateStatus(id, request.status());
    }
    private void requireProcess(Long id) {
        if (mapper.exists(id) == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工序不存在");
    }
    private void validateStatus(String status) {
        if (status != null && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工序状态不合法");
        }
    }
    private ProcessMapper.ProcessRecord from(ProcessRequest request) {
        var record = new ProcessMapper.ProcessRecord();
        record.setProcessCode(request.processCode().trim());
        record.setProcessName(request.processName().trim());
        record.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        return record;
    }

    public record ProcessRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String processCode,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String processName,
            @jakarta.validation.constraints.Size(max = 500) String description) { }
    public record StatusRequest(@jakarta.validation.constraints.NotBlank String status) { }
}
