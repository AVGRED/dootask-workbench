package com.workbench.task.web;

import static org.mockito.ArgumentMatchers.any;
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
import com.workbench.task.service.TagService;
import com.workbench.task.web.TagDtos.TagView;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TagControllerTest {

    private final TagService tagService = Mockito.mock(TagService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TagController(tagService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsTags() throws Exception {
        given(tagService.list(3L, 7L)).willReturn(List.of(new TagView(1L, "前端", "#409eff")));

        mockMvc.perform(get("/api/v1/projects/3/tags").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("前端"));
    }

    @Test
    void createTagReturnsView() throws Exception {
        given(tagService.create(eq(3L), eq(7L), any())).willReturn(new TagView(2L, "后端", ""));

        mockMvc.perform(post("/api/v1/projects/3/tags").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"后端\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("后端"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects/3/tags").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void attachDelegates() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/tags").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(tagService).attach(5L, 7L, 2L);
    }

    @Test
    void attachRejectedForViewer() throws Exception {
        Mockito.doThrow(new BusinessException(403, "只读成员不能修改该项目的任务"))
                .when(tagService).attach(5L, 7L, 2L);

        mockMvc.perform(post("/api/v1/tasks/5/tags").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void detachDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/5/tags/2").principal(principal))
                .andExpect(status().isOk());

        verify(tagService).detach(5L, 7L, 2L);
    }
}
