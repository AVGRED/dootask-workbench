package com.workbench.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.auth.service.AuthService;
import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 独立 MockMvc 测试：不启动 Spring 上下文（启动类在 workbench-app，不在本模块）。
 */
class AuthControllerTest {

    private final AuthService authService = Mockito.mock(AuthService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void loginReturnsToken() throws Exception {
        given(authService.login(any())).willReturn(
                new LoginResponse("token-abc", 7L, "a@b.com", "张三", "staff", 43200L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a@b.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("token-abc"))
                .andExpect(jsonPath("$.data.role").value("staff"));
    }

    @Test
    void loginWithWrongPasswordReturnsBusinessError() throws Exception {
        given(authService.login(any())).willThrow(new BusinessException("账号或密码错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a@b.com\",\"password\":\"bad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void blankPasswordIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a@b.com\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
