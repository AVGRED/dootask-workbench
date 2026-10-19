package com.workbench.planning.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.service.DependencyService;
import com.workbench.planning.web.DependencyDtos.AddDependencyRequest;
import com.workbench.planning.web.DependencyDtos.DependencyView;
import com.workbench.planning.web.DependencyDtos.RiskSummaryView;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/dependencies")
public class DependencyController {

    private final DependencyService dependencyService;

    public DependencyController(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    @GetMapping
    public ApiResponse<List<DependencyView>> list(@PathVariable long projectId) {
        return ApiResponse.ok(dependencyService.list(projectId));
    }

    @PostMapping
    public ApiResponse<DependencyView> add(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody AddDependencyRequest request) {
        return ApiResponse.ok(dependencyService.add(currentUserid(principal), projectId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable long projectId, @PathVariable long id) {
        dependencyService.remove(projectId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/risk-summary")
    public ApiResponse<RiskSummaryView> riskSummary(@PathVariable long projectId) {
        return ApiResponse.ok(dependencyService.riskSummary(projectId));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
