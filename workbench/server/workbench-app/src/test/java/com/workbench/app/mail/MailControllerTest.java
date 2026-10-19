package com.workbench.app.mail;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.common.mail.MailService;
import com.workbench.user.service.SystemAdmin;
import org.springframework.jdbc.core.JdbcTemplate;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class MailControllerTest {

    private final MailService mailService = Mockito.mock(MailService.class);
    private final SystemAdmin systemAdmin = Mockito.mock(SystemAdmin.class);
    private final JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MailController(mailService, systemAdmin, jdbcTemplate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void testReturnsDisabledMode() throws Exception {
        given(jdbcTemplate.queryForList("select email from users where userid = ?", String.class, 7L))
                .willReturn(java.util.List.of("demo@workbench.local"));
        given(mailService.send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).willReturn(false);
        given(mailService.mode()).willReturn("disabled");

        mockMvc.perform(post("/api/v1/admin/mail/test").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sent").value(false))
                .andExpect(jsonPath("$.data.mode").value("disabled"));
    }

    @Test
    void testRejectedForNonAdmin() throws Exception {
        Mockito.doThrow(new BusinessException(403, "需要管理员权限")).when(systemAdmin).requireAdmin(7L);

        mockMvc.perform(post("/api/v1/admin/mail/test").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/mail/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
