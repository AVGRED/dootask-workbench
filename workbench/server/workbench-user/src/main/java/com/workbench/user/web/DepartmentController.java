package com.workbench.user.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.user.service.DepartmentService;
import com.workbench.user.web.DepartmentDtos.DepartmentView;
import com.workbench.user.web.DepartmentDtos.SaveDepartmentRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ApiResponse<List<DepartmentView>> list() {
        return ApiResponse.ok(departmentService.list());
    }

    @PostMapping
    public ApiResponse<DepartmentView> create(@Valid @RequestBody SaveDepartmentRequest request) {
        return ApiResponse.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DepartmentView> update(@PathVariable long id, @Valid @RequestBody SaveDepartmentRequest request) {
        return ApiResponse.ok(departmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        departmentService.delete(id);
        return ApiResponse.ok(null);
    }
}
