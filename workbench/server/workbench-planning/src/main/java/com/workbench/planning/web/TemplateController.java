package com.workbench.planning.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.service.TemplateService;
import com.workbench.planning.web.TemplateDtos.ApplyResult;
import com.workbench.planning.web.TemplateDtos.RevisionView;
import com.workbench.planning.web.TemplateDtos.SaveTemplateRequest;
import com.workbench.planning.web.TemplateDtos.TemplateView;
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
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/projects/{projectId}/templates")
    public ApiResponse<List<TemplateView>> list(@PathVariable long projectId) {
        return ApiResponse.ok(templateService.list(projectId));
    }

    @PostMapping("/projects/{projectId}/templates")
    public ApiResponse<TemplateView> create(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody SaveTemplateRequest request) {
        return ApiResponse.ok(templateService.create(currentUserid(principal), projectId, request));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<TemplateView> update(
            Principal principal,
            @PathVariable long id,
            @Valid @RequestBody SaveTemplateRequest request) {
        return ApiResponse.ok(templateService.update(currentUserid(principal), id, request));
    }

    @GetMapping("/templates/{id}/revisions")
    public ApiResponse<List<RevisionView>> revisions(@PathVariable long id) {
        return ApiResponse.ok(templateService.revisions(id));
    }

    @PostMapping("/templates/{id}/apply")
    public ApiResponse<ApplyResult> apply(Principal principal, @PathVariable long id) {
        return ApiResponse.ok(templateService.apply(currentUserid(principal), id));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        templateService.delete(id);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
