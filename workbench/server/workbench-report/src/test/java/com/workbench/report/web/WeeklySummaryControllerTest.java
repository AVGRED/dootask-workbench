package com.workbench.report.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.report.service.WeeklySummaryService;
import com.workbench.report.web.ReportDtos.WeeklySummaryView;
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

class WeeklySummaryControllerTest {

    private final WeeklySummaryService service = Mockito.mock(WeeklySummaryService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WeeklySummaryController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsSummaries() throws Exception {
        given(service.list(7L)).willReturn(List.of(new WeeklySummaryView(
                1L, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), 4, 1, 480, "本周提交 4 篇")));

        mockMvc.perform(get("/api/v1/weekly-summaries").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reportCount").value(4))
                .andExpect(jsonPath("$.data[0].missingCount").value(1));
    }

    @Test
    void regenerateUsesProvidedWeek() throws Exception {
        given(service.generateForUser(any(Long.class), any(LocalDate.class))).willReturn(new WeeklySummaryView(
                2L, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), 3, 2, 360, "内容"));

        mockMvc.perform(post("/api/v1/weekly-summaries/regenerate").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weekStart\":\"2026-10-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/weekly-summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
