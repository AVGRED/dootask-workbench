package com.workbench.task.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.task.service.BatchService;
import com.workbench.task.web.BatchDtos.BatchResult;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BatchControllerTest {

    private final BatchService batchService = Mockito.mock(BatchService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BatchController(batchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void batchCompleteReturnsAffected() throws Exception {
        given(batchService.batch(eq(3L), eq(7L), any())).willReturn(new BatchResult(2, "complete"));

        mockMvc.perform(post("/api/v1/projects/3/tasks/batch").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"complete\",\"taskIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affected").value(2));
    }

    @Test
    void batchRejectsEmptySelection() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/tasks/batch").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"complete\",\"taskIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void batchRejectsViewer() throws Exception {
        given(batchService.batch(eq(3L), eq(7L), any()))
                .willThrow(new BusinessException(403, "只读成员不能修改该项目的任务"));

        mockMvc.perform(post("/api/v1/projects/3/tasks/batch").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"delete\",\"taskIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void exportReturnsCsvWithBom() throws Exception {
        given(batchService.exportTasksCsv(3L, 7L)).willReturn("id,name\n1,写文档\n");

        mockMvc.perform(get("/api/v1/projects/3/tasks/export").principal(principal))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("tasks.csv")));
    }
}
