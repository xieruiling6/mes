package com.gongqing.mes.modules.product;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.api.PageResult;
import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductService {
    private final ProductMapper mapper;

    public ProductService(ProductMapper mapper) { this.mapper = mapper; }

    public PageResult<ProductMapper.ProductView> list(PageQuery page, String keyword, String status) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String code = normalized;
        String name = normalized;
        validateStatus(status);
        long offset = (page.getPage() - 1) * page.getSize();
        return new PageResult<>(mapper.findPage(code, name, status, page.getSize(), offset), page.getPage(),
                page.getSize(), mapper.count(code, name, status));
    }

    public ProductMapper.ProductView detail(Long id) {
        ProductMapper.ProductView product = mapper.findById(id);
        if (product == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "产品不存在");
        return product;
    }

    public Long create(ProductRequest request, Long operatorId) {
        ProductMapper.ProductRecord record = from(request);
        record.setCreatedBy(operatorId);
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "产品编码已存在");
        }
        return record.getId();
    }

    public void update(Long id, ProductRequest request, Long operatorId) {
        requireProduct(id);
        ProductMapper.ProductRecord record = from(request);
        record.setId(id);
        record.setUpdatedBy(operatorId);
        try {
            mapper.update(record);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "产品编码已存在");
        }
    }

    public void changeStatus(Long id, StatusRequest request, Long operatorId) {
        requireProduct(id);
        validateStatus(request.status());
        mapper.updateStatus(id, request.status(), operatorId);
    }

    private void requireProduct(Long id) {
        if (mapper.exists(id) == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "产品不存在");
    }
    private void validateStatus(String status) {
        if (status != null && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "产品状态不合法");
        }
    }
    private ProductMapper.ProductRecord from(ProductRequest request) {
        ProductMapper.ProductRecord record = new ProductMapper.ProductRecord();
        record.setProductCode(request.productCode().trim());
        record.setProductName(request.productName().trim());
        record.setSpecification(StringUtils.hasText(request.specification()) ? request.specification().trim() : null);
        record.setUnit(request.unit().trim());
        return record;
    }

    public record ProductRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40) String productCode,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String productName,
            @jakarta.validation.constraints.Size(max = 120) String specification,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String unit) { }
    public record StatusRequest(@jakarta.validation.constraints.NotBlank String status) { }
}
