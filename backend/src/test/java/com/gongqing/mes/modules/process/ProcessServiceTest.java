package com.gongqing.mes.modules.process;

import com.gongqing.mes.common.api.PageQuery;
import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessServiceTest {
    private final ProcessMapper mapper = mock(ProcessMapper.class);
    private final ProcessService service = new ProcessService(mapper);

    @Test
    void listTrimsKeywordAndKeepsStatusFilter() {
        when(mapper.findPage("焊接", "INACTIVE", 20, 0)).thenReturn(List.of());
        when(mapper.count("焊接", "INACTIVE")).thenReturn(0L);
        var result = service.list(new PageQuery(), " 焊接 ", "INACTIVE");
        assertThat(result.total()).isZero();
        verify(mapper).findPage("焊接", "INACTIVE", 20, 0);
    }

    @Test
    void statusChangeRejectsUnknownValues() {
        when(mapper.exists(3L)).thenReturn(3L);
        assertThatThrownBy(() -> service.changeStatus(3L, new ProcessService.StatusRequest("DELETED")))
                .isInstanceOf(BusinessException.class);
    }
}
