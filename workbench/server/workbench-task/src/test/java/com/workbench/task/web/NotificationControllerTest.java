package com.workbench.task.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.task.service.NotificationService;
import com.workbench.task.web.CommentDtos.NotificationView;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class NotificationControllerTest {

    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsNotifications() throws Exception {
        given(notificationService.list(7L, true)).willReturn(List.of(
                new NotificationView(1L, "mention", "任务 #5 有新评论", "{}", false,
                        LocalDateTime.of(2026, 10, 19, 10, 0))));

        mockMvc.perform(get("/api/v1/notifications").principal(principal).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("mention"))
                .andExpect(jsonPath("$.data[0].read").value(false));
    }

    @Test
    void unreadCountReturnsNumber() throws Exception {
        given(notificationService.unreadCount(7L)).willReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(3));
    }

    @Test
    void markReadDelegates() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/9/read").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(notificationService).markRead(7L, 9L);
    }

    @Test
    void readAllReturnsAffectedCount() throws Exception {
        given(notificationService.markAllRead(7L)).willReturn(4);

        mockMvc.perform(post("/api/v1/notifications/read-all").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(4));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
