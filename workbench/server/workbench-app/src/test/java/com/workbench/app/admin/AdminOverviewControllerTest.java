package com.workbench.app.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.user.service.SystemAdmin;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AdminOverviewControllerTest {

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final SystemAdmin systemAdmin = Mockito.mock(SystemAdmin.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminOverviewController(jdbc, systemAdmin))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void projectsReturnsRows() throws Exception {
        given(jdbc.queryForList(anyString())).willReturn(List.of(
                Map.of("id", 3, "name", "迭代", "owner_name", "演示用户", "members", 2, "total", 10, "done", 6)));

        mockMvc.perform(get("/api/v1/admin/projects").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("迭代"))
                .andExpect(jsonPath("$.data[0].done").value(6));
    }

    @Test
    void reportsReturnsRows() throws Exception {
        given(jdbc.queryForList(anyString(), any(Object[].class))).willReturn(List.of(
                Map.of("id", 1, "userid", 7, "nickname", "张三", "content_done", "写文档")));

        mockMvc.perform(get("/api/v1/admin/reports").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("张三"));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void nonAdminIsRejected() throws Exception {
        Mockito.doThrow(new com.workbench.common.api.BusinessException(403, "需要管理员权限"))
                .when(systemAdmin).requireAdmin(7L);

        mockMvc.perform(get("/api/v1/admin/reports").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }
}
