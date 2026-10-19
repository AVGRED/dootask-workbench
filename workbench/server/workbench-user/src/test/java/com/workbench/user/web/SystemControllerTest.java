package com.workbench.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.user.service.DictService;
import com.workbench.user.service.SettingsService;
import com.workbench.user.web.SystemDtos.DictItemView;
import com.workbench.user.web.SystemDtos.PublicSettingsView;
import com.workbench.user.web.SystemDtos.SettingView;
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

class SystemControllerTest {

    private final SettingsService settingsService = Mockito.mock(SettingsService.class);
    private final DictService dictService = Mockito.mock(DictService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SystemController(settingsService, dictService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void settingsListedForAdmin() throws Exception {
        given(settingsService.list(7L)).willReturn(List.of(
                new SettingView("app.name", "工作台", "站点名称", LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/admin/settings").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("app.name"));
    }

    @Test
    void settingsRejectedForNonAdmin() throws Exception {
        given(settingsService.list(7L)).willThrow(new BusinessException(403, "需要管理员权限"));

        mockMvc.perform(get("/api/v1/admin/settings").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void saveSettingReturnsValue() throws Exception {
        given(settingsService.upsert(eq(7L), eq("app.name"), any(), any()))
                .willReturn(new SettingView("app.name", "新名字", "", LocalDateTime.now()));

        mockMvc.perform(put("/api/v1/admin/settings/app.name").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"新名字\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("新名字"));
    }

    @Test
    void saveSettingRejectsBlankValue() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings/app.name").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void publicSettingsAreAnonymous() throws Exception {
        given(settingsService.publicSettings()).willReturn(new PublicSettingsView(List.of(
                new SettingView("ui.theme", "light", "", LocalDateTime.now()))));

        mockMvc.perform(get("/api/v1/system/public/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].key").value("ui.theme"));
    }

    @Test
    void dictByTypeReturnsEnabledItems() throws Exception {
        given(dictService.enabled("task_priority")).willReturn(List.of(
                new DictItemView(1L, "task_priority", "2", "紧急", 2, true)));

        mockMvc.perform(get("/api/v1/dicts/task_priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("紧急"));
    }

    @Test
    void createDictReturnsItem() throws Exception {
        given(dictService.create(eq(7L), any())).willReturn(
                new DictItemView(9L, "task_priority", "3", "阻塞", 3, true));

        mockMvc.perform(post("/api/v1/admin/dicts").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"task_priority\",\"code\":\"3\",\"label\":\"阻塞\",\"sort\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    void createDictRejectsBlankCode() throws Exception {
        mockMvc.perform(post("/api/v1/admin/dicts").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"task_priority\",\"code\":\"\",\"label\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteDictRejectsNonAdmin() throws Exception {
        Mockito.doThrow(new BusinessException(403, "需要管理员权限")).when(dictService).delete(7L, 9L);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/dicts/9").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }
}
