package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.security.SecurityConfiguration;
import com.gongqing.mes.common.security.ApiSecurityWriter;
import com.gongqing.mes.common.web.ApiResponseFactory;
import com.gongqing.mes.modules.system.SystemAccessController;
import com.gongqing.mes.modules.system.SystemAccessService;
import com.gongqing.mes.modules.product.ProductController;
import com.gongqing.mes.modules.product.ProductService;
import com.gongqing.mes.modules.product.ProductActorResolver;
import com.gongqing.mes.modules.process.ProcessController;
import com.gongqing.mes.modules.process.ProcessService;
import com.gongqing.mes.modules.order.ProductionOrderController;
import com.gongqing.mes.modules.order.ProductionOrderService;
import com.gongqing.mes.modules.report.ReportController;
import com.gongqing.mes.modules.report.ReportService;
import com.gongqing.mes.modules.quality.QualityController;
import com.gongqing.mes.modules.quality.QualityService;
import com.gongqing.mes.modules.equipment.EquipmentController;
import com.gongqing.mes.modules.equipment.EquipmentService;
import com.gongqing.mes.modules.statistics.StatisticsController;
import com.gongqing.mes.modules.statistics.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, AccountController.class, SystemAccessController.class, ProductController.class, ProcessController.class,
        ProductionOrderController.class, ReportController.class, QualityController.class, EquipmentController.class,
        StatisticsController.class})
@Import({SecurityConfiguration.class, ApiSecurityWriter.class})
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;

    @MockitoBean DatabaseUserDetailsService databaseUserDetailsService;
    @MockitoBean AuthController authController;
    @MockitoBean UserMapper userMapper;
    @MockitoBean AccountService accountService;
    @MockitoBean ApiResponseFactory responseFactory;
    @MockitoBean SystemAccessService systemAccessService;
    @MockitoBean ProductService productService;
    @MockitoBean ProductActorResolver productActorResolver;
    @MockitoBean ProcessService processService;
    @MockitoBean ProductionOrderService productionOrderService;
    @MockitoBean ReportService reportService;
    @MockitoBean QualityService qualityService;
    @MockitoBean EquipmentService equipmentService;
    @MockitoBean StatisticsService statisticsService;

    @Test
    void unprotectedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productionStatisticsRequiresStatisticsPermission() throws Exception {
        mvc.perform(get("/api/v1/statistics/dashboard?dateFrom=2026-10-01&dateTo=2026-10-22")
                        .with(user("operator").authorities()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/statistics/dashboard?dateFrom=2026-10-01&dateTo=2026-10-22")
                        .with(user("planner").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("statistics:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void rolePermissionIsCheckedByTheServer() throws Exception {
        mvc.perform(get("/api/v1/system/roles").with(user("operator").authorities()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/system/roles").with(user("admin").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("system:role:manage"))))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPathAndUnsupportedMethodReturnNotFoundAndMethodNotAllowed() throws Exception {
        mvc.perform(get("/api/v1/not-a-real-endpoint").with(user("manager").authorities()))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/products/1")
                        .with(user("manager").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("product:manage")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void invalidMethodParameterAndBodyValuesReturnBadRequestInsteadOfServerError() throws Exception {
        mvc.perform(get("/api/v1/products/0").with(user("manager").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/equipment/1/status").with(user("manager").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("equipment:manage")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("{\"status\":\"MAINTENANCE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void writeRequestRequiresCsrfToken() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .with(user("admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void productAndProcessWriteEndpointsRequireTheirOwnPermissionCodes() throws Exception {
        mvc.perform(get("/api/v1/products").with(user("planner").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/processes").with(user("planner").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/processes").with(user("planner").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("process:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void orderReadConfirmAndCancelUseSeparatePermissions() throws Exception {
        mvc.perform(get("/api/v1/orders").with(user("planner").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("product:read"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders").with(user("planner").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("order:read"))))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/orders/1/confirm")
                        .with(user("manager").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("order:cancel")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/orders/1/confirm")
                        .with(user("manager").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("order:confirm")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/orders/1/cancel")
                        .with(user("manager").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("order:confirm")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/orders/1/cancel")
                        .with(user("manager").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("order:cancel")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void reportEndpointsRequireCreateReadOwnAndVoidPermissions() throws Exception {
        mvc.perform(get("/api/v1/reports/available-work-orders").with(user("operator").authorities()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/available-work-orders").with(user("operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("report:create"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports").with(user("operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("report:read:own"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/mine").with(user("operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("report:read:own"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/filter-options").with(user("operator").authorities()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/reports/1/void").with(user("operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("report:create")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("{\"reason\":\"wrong\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reports").with(user("operator").authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("report:create")))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("{\"requestKey\":\"invalid\",\"workOrderId\":1," +
                                "\"reportDate\":\"2026-10-22\",\"productionQty\":0,\"goodQty\":0," +
                                "\"unqualifiedQty\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void qualityAndEquipmentEndpointsRequireTheirOwnPermissions() throws Exception {
        mvc.perform(get("/api/v1/quality/records").with(user("operator").authorities())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/quality/records").with(user("inspector").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("quality:read")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/equipment").with(user("operator").authorities())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/equipment").with(user("maintainer").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("equipment:read")))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/equipment").with(user("maintainer").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("equipment:read")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"equipmentCode\":\"EQ-1\",\"equipmentName\":\"Lathe\"}"))
                .andExpect(status().isForbidden());
    }
}
