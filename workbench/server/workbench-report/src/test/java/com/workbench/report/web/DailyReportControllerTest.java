package com.workbench.report.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.report.service.DailyReportService;
import com.workbench.report.web.ReportDtos.DailyReportView;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DailyReportControllerTest {

    private final DailyReportService service = Mockito.mock(DailyReportService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DailyReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void saveReturnsReport() throws Exception {
        given(service.save(eq(7L), any(), any())).willReturn(new DailyReportView(
                1L, LocalDate.of(2026, 10, 19), "完成 A", "", "", 120, 1, "准时"));

        mockMvc.perform(post("/api/v1/daily-reports").principal(principal)
                        .param("date", "2026-10-19")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentDone\":\"完成 A\",\"minutes\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentDone").value("完成 A"))
                .andExpect(jsonPath("$.data.statusText").value("准时"));
    }

    @Test
    void saveRejectsBlankDone() throws Exception {
        mockMvc.perform(post("/api/v1/daily-reports").principal(principal)
                        .param("date", "2026-10-19")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentDone\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void saveRejectsOutsideWindow() throws Exception {
        given(service.save(eq(7L), any(), any()))
                .willThrow(new BusinessException("超过 7 天补交窗口"));

        mockMvc.perform(post("/api/v1/daily-reports").principal(principal)
                        .param("date", "2026-09-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentDone\":\"补写\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("超过 7 天补交窗口"));
    }

    @Test
    void listReturnsRange() throws Exception {
        given(service.range(eq(7L), any(), any())).willReturn(List.of(
                new DailyReportView(1L, LocalDate.of(2026, 10, 19), "完成 A", "", "", 120, 1, "准时")));

        mockMvc.perform(get("/api/v1/daily-reports").principal(principal)
                        .param("from", "2026-10-13").param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value(1));
    }

    @Test
    void byDateReturnsNullWhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/daily-reports/2026-10-19").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/daily-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
