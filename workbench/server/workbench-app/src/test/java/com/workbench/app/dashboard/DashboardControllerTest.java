package com.workbench.app.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.app.dashboard.DashboardDtos.DashboardView;
import com.workbench.app.dashboard.DashboardDtos.LayoutItem;
import com.workbench.app.dashboard.DashboardDtos.LayoutView;
import com.workbench.app.dashboard.DashboardDtos.WidgetDef;
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

class DashboardControllerTest {

    private final DashboardService dashboardService = Mockito.mock(DashboardService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void meReturnsCatalogLayoutAndData() throws Exception {
        given(dashboardService.me(7L)).willReturn(new DashboardView(
                List.of(new WidgetDef("my-tasks", "我的任务", "工作", 6, 2)),
                List.of(new LayoutItem("my-tasks", 0, 0, 6, 2)),
                Map.of("summary", Map.of("projects", 2)),
                "focus", "写代码"));

        mockMvc.perform(get("/api/v1/dashboard/me").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catalog[0].id").value("my-tasks"))
                .andExpect(jsonPath("$.data.layout[0].w").value(6))
                .andExpect(jsonPath("$.data.status").value("focus"));
    }

    @Test
    void saveLayoutReturnsItems() throws Exception {
        given(dashboardService.saveLayout(any(Long.class), any())).willReturn(
                new LayoutView(List.of(new LayoutItem("my-tasks", 1, 1, 4, 2))));

        mockMvc.perform(put("/api/v1/dashboard/me/layout").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":\"my-tasks\",\"x\":1,\"y\":1,\"w\":4,\"h\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("my-tasks"));
    }

    @Test
    void saveLayoutRejectsUnknownWidget() throws Exception {
        given(dashboardService.saveLayout(any(Long.class), any()))
                .willThrow(new BusinessException("未知板块：nope"));

        mockMvc.perform(put("/api/v1/dashboard/me/layout").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":\"nope\",\"x\":0,\"y\":0,\"w\":4,\"h\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void resetReturnsDefaultLayout() throws Exception {
        given(dashboardService.resetLayout(7L)).willReturn(
                new LayoutView(List.of(new LayoutItem("my-tasks", 0, 0, 6, 2))));

        mockMvc.perform(post("/api/v1/dashboard/me/layout/reset").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("my-tasks"));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
