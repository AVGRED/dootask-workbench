package com.workbench.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.user.service.SystemAdmin;
import java.security.Principal;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.common.api.PageResult;
import com.workbench.user.service.UserService;
import com.workbench.user.web.UserDtos.UserView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class UserControllerTest {

    private final UserService userService = Mockito.mock(UserService.class);
    private final SystemAdmin systemAdmin = Mockito.mock(SystemAdmin.class);
    private final Principal principal = () -> "1";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService, systemAdmin))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsPage() throws Exception {
        given(userService.page(anyInt(), anyInt(), any())).willReturn(
                PageResult.of(List.of(new UserView(7L, "a@b.com", "张三", "开发", "", 3, "staff")), 1, 1, 20));

        mockMvc.perform(get("/api/v1/users").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].nickname").value("张三"));
    }

    @Test
    void createRejectsBlankEmail() throws Exception {
        mockMvc.perform(post("/api/v1/users").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"nickname\":\"张三\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsDuplicateEmail() throws Exception {
        given(userService.create(any())).willThrow(new BusinessException("邮箱已被使用"));

        mockMvc.perform(post("/api/v1/users").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"nickname\":\"张三\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮箱已被使用"));
    }

    @Test
    void createRejectsNonAdmin() throws Exception {
        Mockito.doThrow(new BusinessException(403, "需要管理员权限"))
                .when(systemAdmin).requireAdmin(1L);

        mockMvc.perform(post("/api/v1/users").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@y.com\",\"nickname\":\"x\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void deleteDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/v1/users/7").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(userService).delete(anyLong());
    }

    @Test
    void updatePassesNickname() throws Exception {
        given(userService.update(anyLong(), any())).willReturn(
                new UserView(7L, "a@b.com", "李四", "开发", "", 3, "staff"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/7").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"李四\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("李四"));
    }

    @Test
    void listUsesKeyword() throws Exception {
        given(userService.page(anyInt(), anyInt(), anyString())).willReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/v1/users").param("keyword", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void changeRoleReturnsUpdatedUser() throws Exception {
        Principal principal = () -> "7";
        given(userService.changeRole(7L, 7L, "admin")).willReturn(
                new UserView(7L, "a@b.com", "张三", "开发", "", 3, "admin"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/7/role").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"));
    }

    @Test
    void changeRoleRejectsNonAdmin() throws Exception {
        Principal principal = () -> "7";
        given(userService.changeRole(7L, 9L, "admin"))
                .willThrow(new BusinessException(403, "需要管理员权限"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/9/role").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void changeRoleRejectsInvalidRole() throws Exception {
        Principal principal = () -> "7";
        given(userService.changeRole(7L, 7L, "root"))
                .willThrow(new BusinessException("角色只能是 admin 或 staff"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/users/7/role").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"root\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
