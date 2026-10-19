package com.workbench.planning.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.planning.service.ScheduleApplyService;
import com.workbench.planning.web.ScheduleApplyDtos.BatchView;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ScheduleApplyControllerTest {

    private final ScheduleApplyService service = Mockito.mock(ScheduleApplyService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScheduleApplyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void applyReturnsBatch() throws Exception {
        given(service.apply(eq(7L), eq(3L), any())).willReturn(
                new BatchView(1L, "req-1", 2, LocalDateTime.now(), false));

        mockMvc.perform(post("/api/v1/projects/3/schedule/apply").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"anchorTaskId\":1,"
                                + "\"anchorStart\":\"2026-10-05T09:00:00\",\"anchorEnd\":\"2026-10-07T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changeCount").value(2))
                .andExpect(jsonPath("$.data.undone").value(false));
    }

    @Test
    void applyRejectsMissingRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/schedule/apply").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anchorTaskId\":1,\"anchorStart\":\"2026-10-05T09:00:00\","
                                + "\"anchorEnd\":\"2026-10-07T18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void undoReturnsUndoneBatch() throws Exception {
        given(service.undo(7L, 1L)).willReturn(new BatchView(1L, "req-1", 2, LocalDateTime.now(), true));

        mockMvc.perform(post("/api/v1/schedule/batches/1/undo").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.undone").value(true));
    }

    @Test
    void undoTwiceIsRejected() throws Exception {
        given(service.undo(7L, 1L)).willThrow(new BusinessException("该批次已撤销"));

        mockMvc.perform(post("/api/v1/schedule/batches/1/undo").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该批次已撤销"));
    }

    @Test
    void batchesAreListed() throws Exception {
        given(service.batches(3L)).willReturn(List.of(
                new BatchView(2L, "req-2", 1, LocalDateTime.now(), false),
                new BatchView(1L, "req-1", 2, LocalDateTime.now(), true)));

        mockMvc.perform(get("/api/v1/projects/3/schedule/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value("req-2"))
                .andExpect(jsonPath("$.data[1].undone").value(true));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/schedule/batches/1/undo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(service, Mockito.never()).undo(anyLong(), anyLong());
    }
}
