package com.workbench.project.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.common.api.PageResult;
import com.workbench.project.service.ActivityService;
import com.workbench.project.web.ActivityDtos.ActivityView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ActivityControllerTest {

    private final ActivityService activityService = Mockito.mock(ActivityService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActivityController(activityService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsActivities() throws Exception {
        given(activityService.page(3L, null, 1, 20)).willReturn(PageResult.of(
                List.of(new ActivityView(1L, 5L, 7L, "task.create", "创建任务", "创建任务：写文档",
                        LocalDateTime.of(2026, 10, 19, 10, 0))),
                1, 1, 20));

        mockMvc.perform(get("/api/v1/projects/3/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].action").value("task.create"))
                .andExpect(jsonPath("$.data.list[0].actionText").value("创建任务"));
    }

    @Test
    void listFiltersByTask() throws Exception {
        given(activityService.page(3L, 5L, 1, 10)).willReturn(PageResult.of(
                List.of(new ActivityView(2L, 5L, 7L, "task.complete", "完成任务", "完成任务", LocalDateTime.now())),
                1, 1, 10));

        mockMvc.perform(get("/api/v1/projects/3/activities").param("taskId", "5").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].action").value("task.complete"));
    }

    @Test
    void actionTextFallback() {
        org.junit.jupiter.api.Assertions.assertEquals("unknown.action", ActivityService.textOf("unknown.action"));
        org.junit.jupiter.api.Assertions.assertEquals("创建任务", ActivityService.textOf("task.create"));
    }

    @Test
    void emptyList() throws Exception {
        given(activityService.page(Mockito.eq(4L), Mockito.any(), anyInt(), anyInt()))
                .willReturn(PageResult.of(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/v1/projects/4/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
