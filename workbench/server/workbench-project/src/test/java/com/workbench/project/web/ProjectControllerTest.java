package com.workbench.project.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.workbench.common.api.PageResult;
import com.workbench.project.service.ProjectService;
import com.workbench.project.web.ProjectDtos.MemberView;
import com.workbench.project.web.ProjectDtos.ProjectView;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ProjectControllerTest {

    private final ProjectService projectService = Mockito.mock(ProjectService.class);
    private final Principal principal = () -> "7";
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProjectController(projectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listUsesCurrentUser() throws Exception {
        given(projectService.page(eq(7L), anyInt(), anyInt(), any()))
                .willReturn(PageResult.of(List.of(new ProjectView(1L, "工作台", "说明", 7L, false, 2)), 1, 1, 20));

        mockMvc.perform(get("/api/v1/projects").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].name").value("工作台"))
                .andExpect(jsonPath("$.data.list[0].memberCount").value(2));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/projects").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createPassesOwner() throws Exception {
        given(projectService.create(eq(7L), any()))
                .willReturn(new ProjectView(9L, "新项目", "", 7L, false, 1));

        mockMvc.perform(post("/api/v1/projects").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新项目\",\"desc\":\"说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    void deleteRequiresOwnership() throws Exception {
        Mockito.doThrow(new BusinessException("无权操作该项目"))
                .when(projectService).delete(eq(7L), eq(1L));

        mockMvc.perform(delete("/api/v1/projects/1").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无权操作该项目"));
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/projects/3").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(projectService).delete(7L, 3L);
    }

    @Test
    void membersAreListed() throws Exception {
        given(projectService.members(3L)).willReturn(List.of(new MemberView(7L, true, "owner"),
                new MemberView(9L, false, "viewer")));

        mockMvc.perform(get("/api/v1/projects/3/members").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].owner").value(true))
                .andExpect(jsonPath("$.data[1].userid").value(9))
                .andExpect(jsonPath("$.data[1].role").value("viewer"));
    }

    @Test
    void addMemberPassesRequest() throws Exception {
        given(projectService.addMember(eq(7L), eq(3L), any())).willReturn(new MemberView(11L, false, "member"));

        mockMvc.perform(post("/api/v1/projects/3/members").principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userid\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userid").value(11));
    }

    @Test
    void removeOwnerIsRejectedByService() throws Exception {
        Mockito.doThrow(new BusinessException("不能移除项目负责人"))
                .when(projectService).removeMember(eq(7L), eq(3L), eq(7L));

        mockMvc.perform(delete("/api/v1/projects/3/members/7").principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("不能移除项目负责人"));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
