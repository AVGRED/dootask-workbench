package com.workbench.task.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.task.service.TagService;
import com.workbench.task.web.TagDtos.AttachTagRequest;
import com.workbench.task.web.TagDtos.SaveTagRequest;
import com.workbench.task.web.TagDtos.TagView;
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
@RequestMapping("/api/v1")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/projects/{projectId}/tags")
    public ApiResponse<List<TagView>> list(Principal principal, @PathVariable long projectId) {
        return ApiResponse.ok(tagService.list(projectId, currentUserid(principal)));
    }

    @PostMapping("/projects/{projectId}/tags")
    public ApiResponse<TagView> create(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody SaveTagRequest request) {
        return ApiResponse.ok(tagService.create(projectId, currentUserid(principal), request));
    }

    @DeleteMapping("/projects/{projectId}/tags/{tagId}")
    public ApiResponse<Void> delete(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long tagId) {
        tagService.delete(projectId, currentUserid(principal), tagId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/tasks/{taskId}/tags")
    public ApiResponse<List<TagView>> tagsOfTask(Principal principal, @PathVariable long taskId) {
        return ApiResponse.ok(tagService.tagsOfTask(taskId, currentUserid(principal)));
    }

    @PostMapping("/tasks/{taskId}/tags")
    public ApiResponse<Void> attach(
            Principal principal,
            @PathVariable long taskId,
            @Valid @RequestBody AttachTagRequest request) {
        tagService.attach(taskId, currentUserid(principal), request.tagId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/tasks/{taskId}/tags/{tagId}")
    public ApiResponse<Void> detach(
            Principal principal,
            @PathVariable long taskId,
            @PathVariable long tagId) {
        tagService.detach(taskId, currentUserid(principal), tagId);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
