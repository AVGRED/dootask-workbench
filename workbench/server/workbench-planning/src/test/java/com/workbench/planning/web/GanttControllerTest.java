package com.workbench.planning.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.planning.service.GanttService;
import com.workbench.planning.web.GanttDtos.GanttEdge;
import com.workbench.planning.web.GanttDtos.GanttTask;
import com.workbench.planning.web.GanttDtos.GanttView;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class GanttControllerTest {

    private final GanttService ganttService = Mockito.mock(GanttService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GanttController(ganttService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsTasksAndEdges() throws Exception {
        given(ganttService.view(3L, 7L)).willReturn(new GanttView(3L,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10),
                List.of(new GanttTask(5L, "设计", LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 4), 7L, 1, false, 9L)),
                List.of(new GanttEdge(5L, 6L))));

        mockMvc.perform(get("/api/v1/projects/3/gantt").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].name").value("设计"))
                .andExpect(jsonPath("$.data.tasks[0].startDate").exists())
                .andExpect(jsonPath("$.data.edges[0].successorTaskId").value(6));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects/3/gantt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        Mockito.verify(ganttService, Mockito.never()).view(anyLong(), anyLong());
    }
}
