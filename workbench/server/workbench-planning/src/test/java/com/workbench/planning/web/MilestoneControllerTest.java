package com.workbench.planning.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.planning.service.MilestoneService;
import com.workbench.planning.web.MilestoneDtos.MilestoneView;
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

class MilestoneControllerTest {

    private final MilestoneService milestoneService = Mockito.mock(MilestoneService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MilestoneController(milestoneService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsCounts() throws Exception {
        given(milestoneService.list(3L)).willReturn(List.of(
                new MilestoneView(1L, "上线", "说明", 7L, LocalDate.of(2026, 12, 1), "open", 3, 1, 5, 2)));

        mockMvc.perform(get("/api/v1/projects/3/milestones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("上线"))
                .andExpect(jsonPath("$.data[0].taskCount").value(5))
                .andExpect(jsonPath("$.data[0].completedCount").value(2));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/milestones").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsInvalidStatus() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/milestones").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"上线\",\"status\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createPassesTargetAndReminder() throws Exception {
        given(milestoneService.create(eq(7L), eq(3L), any())).willReturn(
                new MilestoneView(2L, "上线", "", 0L, LocalDate.of(2026, 12, 1), "open", 3, 2, 0, 0));

        mockMvc.perform(post("/api/v1/projects/3/milestones").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"上线\",\"targetAt\":\"2026-12-01\",\"reminderDays\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.reminderDays").value(3));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/3/milestones/2").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(milestoneService).delete(anyLong(), anyLong());
    }
}
