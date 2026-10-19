package com.workbench.worklog.web;

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

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.worklog.service.WorklogService;
import com.workbench.worklog.web.WorklogDtos.CapacityMember;
import com.workbench.worklog.web.WorklogDtos.CapacitySummary;
import com.workbench.worklog.web.WorklogDtos.WorklogView;
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

class WorklogControllerTest {

    private final WorklogService worklogService = Mockito.mock(WorklogService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WorklogController(worklogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsLog() throws Exception {
        given(worklogService.create(eq(7L), eq(5L), any()))
                .willReturn(new WorklogView(1L, 5L, 7L, 120, LocalDate.of(2026, 10, 19), "联调"));

        mockMvc.perform(post("/api/v1/tasks/5/worklogs").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"minutes\":120,\"workDate\":\"2026-10-19\",\"note\":\"联调\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minutes").value(120))
                .andExpect(jsonPath("$.data.taskId").value(5));
    }

    @Test
    void createRejectsTooManyMinutes() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/worklogs").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"minutes\":1441,\"workDate\":\"2026-10-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsMissingRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/worklogs").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":60,\"workDate\":\"2026-10-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateRequestReturnsSameLog() throws Exception {
        WorklogView existing = new WorklogView(3L, 5L, 7L, 60, LocalDate.of(2026, 10, 19), "");
        given(worklogService.create(eq(7L), eq(5L), any())).willReturn(existing);

        mockMvc.perform(post("/api/v1/tasks/5/worklogs").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"req-1\",\"minutes\":60,\"workDate\":\"2026-10-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    void listReturnsLogs() throws Exception {
        given(worklogService.listByTask(eq(5L), anyLong())).willReturn(List.of(
                new WorklogView(1L, 5L, 7L, 120, LocalDate.of(2026, 10, 19), "")));

        mockMvc.perform(get("/api/v1/tasks/5/worklogs").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].minutes").value(120));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/worklogs/9").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(worklogService).delete(7L, 9L);
    }

    @Test
    void deleteRejectsOtherUsersLog() throws Exception {
        Mockito.doThrow(new BusinessException("无权删除该工时记录")).when(worklogService).delete(eq(7L), eq(9L));

        mockMvc.perform(delete("/api/v1/worklogs/9").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无权删除该工时记录"));
    }

    @Test
    void capacityRejectsRangeOver93Days() throws Exception {
        given(worklogService.capacity(anyLong(), anyLong(), any(), any()))
                .willThrow(new BusinessException("日期区间最多 93 天"));

        mockMvc.perform(get("/api/v1/projects/3/capacity").principal(principal)
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日期区间最多 93 天"));
    }

    @Test
    void capacityReturnsMembers() throws Exception {
        given(worklogService.capacity(anyLong(), anyLong(), any(), any())).willReturn(new CapacitySummary(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), 5, 480,
                List.of(new CapacityMember(7L, 540, 2400, 23, 0, 2))));

        mockMvc.perform(get("/api/v1/projects/3/capacity").principal(principal)
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workdays").value(5))
                .andExpect(jsonPath("$.data.members[0].loggedMinutes").value(540))
                .andExpect(jsonPath("$.data.members[0].utilizationPercent").value(23));
    }
}
