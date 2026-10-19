package com.workbench.planning.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.service.MilestoneService;
import com.workbench.planning.web.MilestoneDtos.MilestoneView;
import com.workbench.planning.web.MilestoneDtos.SaveMilestoneRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @GetMapping("/projects/{projectId}/milestones")
    public ApiResponse<List<MilestoneView>> list(@PathVariable long projectId) {
        return ApiResponse.ok(milestoneService.list(projectId));
    }

    @PostMapping("/projects/{projectId}/milestones")
    public ApiResponse<MilestoneView> create(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody SaveMilestoneRequest request) {
        return ApiResponse.ok(milestoneService.create(currentUserid(principal), projectId, request));
    }

    @PutMapping("/projects/{projectId}/milestones/{id}")
    public ApiResponse<MilestoneView> update(
            @PathVariable long projectId,
            @PathVariable long id,
            @Valid @RequestBody SaveMilestoneRequest request) {
        return ApiResponse.ok(milestoneService.update(projectId, id, request));
    }

    @DeleteMapping("/projects/{projectId}/milestones/{id}")
    public ApiResponse<Void> delete(@PathVariable long projectId, @PathVariable long id) {
        milestoneService.delete(projectId, id);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
