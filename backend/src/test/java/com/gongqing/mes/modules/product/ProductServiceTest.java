package com.gongqing.mes.modules.product;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {
    private final ProductMapper mapper = mock(ProductMapper.class);
    private final ProductService service = new ProductService(mapper);

    @Test
    void listUsesSameTrimmedKeywordAndStablePagination() {
        PageQuery page = new PageQuery();
        page.setPage(2); page.setSize(10);
        when(mapper.findPage("P-1", "P-1", "ACTIVE", 10, 10)).thenReturn(List.of());
        when(mapper.count("P-1", "P-1", "ACTIVE")).thenReturn(11L);

        var result = service.list(page, " P-1 ", "ACTIVE");

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(11);
        verify(mapper).findPage("P-1", "P-1", "ACTIVE", 10, 10);
    }

    @Test
    void illegalStatusIsRejectedBeforeDatabaseQuery() {
        assertThatThrownBy(() -> service.list(new PageQuery(), null, "DELETED"))
                .isInstanceOf(BusinessException.class);
    }
}
