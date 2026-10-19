package com.workbench.app.status;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class StatusControllerTest {

    private final StatusService statusService = Mockito.mock(StatusService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StatusController(statusService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void setStatusReturnsUpdated() throws Exception {
        given(statusService.setStatus(7L, "focus", "写代码")).willReturn(Map.of("status", "focus", "note", "写代码"));

        mockMvc.perform(put("/api/v1/me/status").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"focus\",\"note\":\"写代码\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("focus"))
                .andExpect(jsonPath("$.data.note").value("写代码"));
    }

    @Test
    void setStatusRejectsUnknown() throws Exception {
        given(statusService.setStatus(7L, "sleeping", null))
                .willThrow(new BusinessException("状态只能是 focus/idle/meeting/away/leave"));

        mockMvc.perform(put("/api/v1/me/status").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"sleeping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void teamReturnsRows() throws Exception {
        given(statusService.team(7L)).willReturn(List.of(
                Map.of("userid", 7, "nickname", "张三", "status", "focus", "current_task", "写文档")));

        mockMvc.perform(get("/api/v1/team/status").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("张三"))
                .andExpect(jsonPath("$.data[0].current_task").value("写文档"));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/team/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
