package com.workbench.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workbench.common.api.BusinessException;
import com.workbench.common.api.GlobalExceptionHandler;
import com.workbench.user.service.DepartmentService;
import com.workbench.user.web.DepartmentDtos.DepartmentView;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DepartmentControllerTest {

    private final DepartmentService departmentService = Mockito.mock(DepartmentService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DepartmentController(departmentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void listReturnsDepartments() throws Exception {
        given(departmentService.list()).willReturn(List.of(new DepartmentView(1L, "研发部", 0L, 7L, 2L)));

        mockMvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("研发部"))
                .andExpect(jsonPath("$.data[0].childrenCount").value(2));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deleteRejectsParentWithChildren() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException("请先删除子部门")).when(departmentService).delete(1L);

        mockMvc.perform(delete("/api/v1/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先删除子部门"));
    }

    @Test
    void createPassesParent() throws Exception {
        given(departmentService.create(any())).willReturn(new DepartmentView(2L, "前端组", 1L, 0L, 0L));

        mockMvc.perform(post("/api/v1/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"前端组\",\"parentId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2));

        verify(departmentService).create(any());
    }

    @Test
    void deleteDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/departments/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(departmentService).delete(anyLong());
    }
}
