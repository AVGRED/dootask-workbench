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

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.planning.service.DependencyService;
import com.workbench.planning.web.DependencyDtos.DependencyView;
import com.workbench.planning.web.DependencyDtos.RiskSummaryView;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DependencyControllerTest {

    private final DependencyService dependencyService = Mockito.mock(DependencyService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DependencyController(dependencyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsEdges() throws Exception {
        given(dependencyService.list(3L)).willReturn(List.of(new DependencyView(1L, 10L, 11L)));

        mockMvc.perform(get("/api/v1/projects/3/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].predecessorTaskId").value(10))
                .andExpect(jsonPath("$.data[0].successorTaskId").value(11));
    }

    @Test
    void addRejectsMissingTask() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/dependencies").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorTaskId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void addRejectsSelfDependency() throws Exception {
        given(dependencyService.add(eq(7L), eq(3L), any()))
                .willThrow(new BusinessException("不能把任务依赖到自己"));

        mockMvc.perform(post("/api/v1/projects/3/dependencies").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorTaskId\":10,\"successorTaskId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("不能把任务依赖到自己"));
    }

    @Test
    void addRejectsCycle() throws Exception {
        given(dependencyService.add(eq(7L), eq(3L), any()))
                .willThrow(new BusinessException("依赖成环，无法添加"));

        mockMvc.perform(post("/api/v1/projects/3/dependencies").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorTaskId\":11,\"successorTaskId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("依赖成环，无法添加"));
    }

    @Test
    void addPassesRequest() throws Exception {
        given(dependencyService.add(eq(7L), eq(3L), any())).willReturn(new DependencyView(9L, 10L, 11L));

        mockMvc.perform(post("/api/v1/projects/3/dependencies").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorTaskId\":10,\"successorTaskId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/3/dependencies/9").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(dependencyService).remove(anyLong(), anyLong());
    }

    @Test
    void riskSummaryReturnsCounts() throws Exception {
        given(dependencyService.riskSummary(3L)).willReturn(new RiskSummaryView(4, 2, 1, 3));

        mockMvc.perform(get("/api/v1/projects/3/dependencies/risk-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edgeCount").value(4))
                .andExpect(jsonPath("$.data.blockedCount").value(2))
                .andExpect(jsonPath("$.data.overdueCount").value(3));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"predecessorTaskId\":10,\"successorTaskId\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
