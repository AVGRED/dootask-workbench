package com.workbench.project.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.PageResult;
import com.workbench.project.service.ProjectService;
import com.workbench.project.web.ProjectDtos.AddMemberRequest;
import com.workbench.project.web.ProjectDtos.MemberView;
import com.workbench.project.web.ProjectDtos.ProjectView;
import com.workbench.project.web.ProjectDtos.SaveProjectRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResponse<PageResult<ProjectView>> list(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(projectService.page(currentUserid(principal), page, size, keyword));
    }

    @PostMapping
    public ApiResponse<ProjectView> create(Principal principal, @Valid @RequestBody SaveProjectRequest request) {
        return ApiResponse.ok(projectService.create(currentUserid(principal), request));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<ProjectView> update(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody SaveProjectRequest request) {
        return ApiResponse.ok(projectService.update(currentUserid(principal), projectId, request));
    }

    @PostMapping("/{projectId}/archive")
    public ApiResponse<Void> archive(Principal principal, @PathVariable long projectId) {
        projectService.archive(currentUserid(principal), projectId, true);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{projectId}/restore")
    public ApiResponse<Void> restore(Principal principal, @PathVariable long projectId) {
        projectService.archive(currentUserid(principal), projectId, false);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(Principal principal, @PathVariable long projectId) {
        projectService.delete(currentUserid(principal), projectId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{projectId}/members")
    public ApiResponse<List<MemberView>> members(@PathVariable long projectId) {
        return ApiResponse.ok(projectService.members(projectId));
    }

    @PostMapping("/{projectId}/members")
    public ApiResponse<MemberView> addMember(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody AddMemberRequest request) {
        return ApiResponse.ok(projectService.addMember(currentUserid(principal), projectId, request));
    }

    @DeleteMapping("/{projectId}/members/{userid}")
    public ApiResponse<Void> removeMember(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long userid) {
        projectService.removeMember(currentUserid(principal), projectId, userid);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new com.workbench.common.api.BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
