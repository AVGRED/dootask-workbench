package com.workbench.planning.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.planning.service.TemplateService;
import com.workbench.planning.web.TemplateDtos.ApplyResult;
import com.workbench.planning.web.TemplateDtos.RevisionView;
import com.workbench.planning.web.TemplateDtos.TemplateView;
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

class TemplateControllerTest {

    private final TemplateService templateService = Mockito.mock(TemplateService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TemplateController(templateService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsRevisionOne() throws Exception {
        given(templateService.create(eq(7L), eq(3L), any())).willReturn(new TemplateView(
                1L, "需求评审", "步骤", "", 1, 1, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/projects/3/templates").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"需求评审\",\"content\":\"步骤\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(1))
                .andExpect(jsonPath("$.data.revisionCount").value(1));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/templates").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateBumpsRevision() throws Exception {
        given(templateService.update(eq(7L), eq(1L), any())).willReturn(new TemplateView(
                1L, "需求评审 v2", "步骤", "", 2, 2, LocalDateTime.now()));

        mockMvc.perform(put("/api/v1/templates/1").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"需求评审 v2\",\"content\":\"步骤\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionNo").value(2))
                .andExpect(jsonPath("$.data.revisionCount").value(2));
    }

    @Test
    void revisionsListedDescending() throws Exception {
        given(templateService.revisions(1L)).willReturn(List.of(
                new RevisionView(2L, 2, "{}", LocalDateTime.now()),
                new RevisionView(1L, 1, "{}", LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/templates/1/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].revisionNo").value(2));
    }

    @Test
    void applyCreatesTask() throws Exception {
        given(templateService.apply(7L, 1L)).willReturn(new ApplyResult(42L, "需求评审"));

        mockMvc.perform(post("/api/v1/templates/1/apply").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(42));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/templates/1")).andExpect(status().isOk());

        verify(templateService).delete(1L);
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/templates/1/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
