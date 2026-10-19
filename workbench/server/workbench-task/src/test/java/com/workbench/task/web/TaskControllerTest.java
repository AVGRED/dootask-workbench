package com.workbench.task.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.workbench.common.api.PageResult;
import com.workbench.task.service.TaskService;
import com.workbench.task.web.TaskDtos.TaskView;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TaskControllerTest {

    private final TaskService taskService = Mockito.mock(TaskService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    private static TaskView view(long id, long projectId, String name, long owner, int pLevel, Long milestoneId) {
        return new TaskView(id, projectId, name, "", owner, null, null, null, pLevel, false, milestoneId, 0L);
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsTasks() throws Exception {
        given(taskService.page(eq(7L), eq(3L), anyInt(), anyInt(), any(), any(), any(), any()))
                .willReturn(PageResult.of(List.of(view(1L, 3L, "写文档", 0L, 0, null)), 1, 1, 20));

        mockMvc.perform(get("/api/v1/projects/3/tasks").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].name").value("写文档"))
                .andExpect(jsonPath("$.data.list[0].completed").value(false));
    }

    @Test
    void listFiltersByMilestone() throws Exception {
        given(taskService.page(eq(7L), eq(3L), anyInt(), anyInt(), any(), any(), eq(12L), any()))
                .willReturn(PageResult.of(List.of(view(1L, 3L, "里程碑任务", 0L, 0, 12L)), 1, 1, 20));

        mockMvc.perform(get("/api/v1/projects/3/tasks").principal(principal).param("milestoneId", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].milestoneId").value(12));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/tasks").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createPassesMilestone() throws Exception {
        given(taskService.create(eq(7L), eq(3L), any())).willReturn(view(5L, 3L, "联调", 9L, 1, 12L));

        mockMvc.perform(post("/api/v1/projects/3/tasks").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"联调\",\"ownerUserid\":9,\"pLevel\":1,\"milestoneId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerUserid").value(9))
                .andExpect(jsonPath("$.data.milestoneId").value(12));
    }

    @Test
    void createRejectsForeignMilestone() throws Exception {
        given(taskService.create(eq(7L), eq(3L), any()))
                .willThrow(new BusinessException("里程碑不存在或不属于该项目"));

        mockMvc.perform(post("/api/v1/projects/3/tasks").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"联调\",\"milestoneId\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("里程碑不存在或不属于该项目"));
    }

    @Test
    void updateClearsMilestone() throws Exception {
        given(taskService.update(eq(7L), eq(5L), any())).willReturn(view(5L, 3L, "联调", 9L, 1, null));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/tasks/5").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"联调\",\"milestoneId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.milestoneId").doesNotExist());
    }

    @Test
    void completeDelegates() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/complete").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).complete(7L, 5L, true);
    }

    @Test
    void reopenDelegates() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/reopen").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).complete(7L, 5L, false);
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/5").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskService).delete(anyLong(), anyLong());
    }

    @Test
    void nonMemberIsRejected() throws Exception {
        Mockito.doThrow(new BusinessException("无权访问该项目"))
                .when(taskService).page(eq(7L), eq(3L), anyInt(), anyInt(), any(), any(), any(), any());

        mockMvc.perform(get("/api/v1/projects/3/tasks").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无权访问该项目"));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects/3/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
