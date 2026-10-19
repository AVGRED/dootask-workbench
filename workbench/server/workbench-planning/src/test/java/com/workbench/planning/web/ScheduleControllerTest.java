package com.workbench.planning.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.planning.service.TaskScheduleService;
import com.workbench.planning.web.ScheduleDtos.ChangeView;
import com.workbench.planning.web.ScheduleDtos.PreviewResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ScheduleControllerTest {

    private final TaskScheduleService scheduleService = Mockito.mock(TaskScheduleService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScheduleController(scheduleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void previewReturnsChanges() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 7, 18, 0);
        given(scheduleService.preview(eq(3L), any())).willReturn(new PreviewResponse(
                List.of(new ChangeView(2, LocalDateTime.of(2026, 10, 6, 9, 0),
                        LocalDateTime.of(2026, 10, 6, 18, 0), start, start.plusHours(9))), 1));

        mockMvc.perform(post("/api/v1/projects/3/schedule/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anchorTaskId\":1,\"anchorStart\":\"2026-10-05T09:00:00\",\"anchorEnd\":\"2026-10-07T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.changeCount").value(1))
                .andExpect(jsonPath("$.data.changes[0].taskId").value(2));
    }

    @Test
    void previewRejectsMissingAnchor() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/schedule/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anchorStart\":\"2026-10-05T09:00:00\",\"anchorEnd\":\"2026-10-07T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
