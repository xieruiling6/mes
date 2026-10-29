package com.gongqing.mes.modules.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "mes.integration", matches = "true")
class ReportMapperDatabaseSmokeTest {
    @Autowired ReportMapper mapper;

    @Test
    void executesReportQueriesAndLockingStatementsAgainstConfiguredDatabase() {
        assertThat(mapper.findAvailableWorkOrders()).isNotNull();
        assertThat(mapper.findProductOptions(null)).isNotNull();
        assertThat(mapper.findProductOptions(1L)).isNotNull();
        assertThat(mapper.findReporterOptions()).isNotNull();
        assertThat(mapper.findPage(null, null, null, LocalDate.now(), LocalDate.now(), null, 10, 0)).isNotNull();
        assertThat(mapper.countPage(null, null, null, null, null, null)).isGreaterThanOrEqualTo(0);
        assertThat(mapper.sumValidQty(-1L)).isZero();
        assertThat(mapper.findByRequestKey("00000000-0000-4000-8000-000000000000")).isNull();
        assertThat(mapper.lockWorkOrder(-1L)).isNull();
        assertThat(mapper.lockReport(-1L)).isNull();
        assertThat(mapper.summarizeWorkOrder(-1L)).isNull();
    }
}
