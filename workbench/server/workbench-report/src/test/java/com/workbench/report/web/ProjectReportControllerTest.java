package com.workbench.report.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.report.service.ProjectReportService;
import com.workbench.report.web.ReportDtos.DayPoint;
import com.workbench.report.web.ReportDtos.MemberStat;
import com.workbench.report.web.ReportDtos.ProjectReport;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ProjectReportControllerTest {

    private final ProjectReportService reportService = Mockito.mock(ProjectReportService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProjectReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsReport() throws Exception {
        given(reportService.report(anyLong(), anyLong(), any(), any())).willReturn(new ProjectReport(
                3L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                10, 6, 2, 60, 480, 4, 2,
                List.of(new MemberStat(7L, "a@b.com", 300, 3)),
                List.of(new DayPoint(LocalDate.of(2026, 10, 1), 120, 2))));

        mockMvc.perform(get("/api/v1/projects/3/report").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionPercent").value(60))
                .andExpect(jsonPath("$.data.members[0].minutes").value(300))
                .andExpect(jsonPath("$.data.days[0].completedTasks").value(2));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects/3/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
