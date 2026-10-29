package com.gongqing.mes.common;

import com.gongqing.mes.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidationTestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void constraintViolationReturnsUnifiedBadRequest() throws Exception {
        mockMvc.perform(get("/test/validation").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data['validate.count']").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void malformedParameterReturnsUnifiedBadRequest() throws Exception {
        mockMvc.perform(get("/test/validation").param("count", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求格式不正确"));
    }

    @Test
    void pageSizeOverLimitIsRejected() throws Exception {
        mockMvc.perform(get("/test/validation/page").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.data.size").exists());
    }

    @Test
    void databaseConstraintConflictReturnsConflictWithoutDetails() throws Exception {
        mockMvc.perform(get("/test/validation/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message").value("数据冲突，请刷新后重试"));
    }
}
