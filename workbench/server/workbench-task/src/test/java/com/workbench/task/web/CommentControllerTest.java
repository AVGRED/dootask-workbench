package com.workbench.task.web;

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
import com.workbench.task.service.CommentService;
import com.workbench.task.web.CommentDtos.CommentView;
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

class CommentControllerTest {

    private final CommentService commentService = Mockito.mock(CommentService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommentController(commentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsComment() throws Exception {
        given(commentService.create(eq(7L), eq(5L), any())).willReturn(new CommentView(
                1L, 5L, 7L, "看起来不错", List.of(9L), LocalDateTime.of(2026, 10, 19, 10, 0)));

        mockMvc.perform(post("/api/v1/tasks/5/comments").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"看起来不错\",\"mentionUserids\":[9]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("看起来不错"))
                .andExpect(jsonPath("$.data.mentionUserids[0]").value(9));
    }

    @Test
    void createRejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/v1/tasks/5/comments").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsUnknownMention() throws Exception {
        given(commentService.create(eq(7L), eq(5L), any()))
                .willThrow(new BusinessException("被 @ 的用户不存在"));

        mockMvc.perform(post("/api/v1/tasks/5/comments").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"@某人\",\"mentionUserids\":[9999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("被 @ 的用户不存在"));
    }

    @Test
    void listReturnsComments() throws Exception {
        given(commentService.list(eq(5L), anyLong())).willReturn(List.of(
                new CommentView(1L, 5L, 7L, "第一条", List.of(), LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/tasks/5/comments").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("第一条"));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/comments/3").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(commentService).delete(7L, 3L);
    }

    @Test
    void deleteRejectsOthers() throws Exception {
        Mockito.doThrow(new BusinessException("无权删除该评论")).when(commentService).delete(7L, 3L);

        mockMvc.perform(delete("/api/v1/comments/3").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无权删除该评论"));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/5/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
