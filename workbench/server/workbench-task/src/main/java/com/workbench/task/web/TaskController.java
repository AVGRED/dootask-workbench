package com.workbench.task.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.common.api.PageResult;
import com.workbench.task.service.TaskService;
import com.workbench.task.web.TaskDtos.SaveTaskRequest;
import com.workbench.task.web.TaskDtos.TaskView;
import jakarta.validation.Valid;
import java.security.Principal;
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
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/projects/{projectId}/tasks")
    public ApiResponse<PageResult<TaskView>> list(
            Principal principal,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerUserid,
            @RequestParam(required = false) Long milestoneId,
            @RequestParam(required = false) Long tagId) {
        return ApiResponse.ok(taskService.page(currentUserid(principal), projectId, page, size, keyword,
                ownerUserid, milestoneId, tagId));
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ApiResponse<TaskView> create(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody SaveTaskRequest request) {
        return ApiResponse.ok(taskService.create(currentUserid(principal), projectId, request));
    }

    @GetMapping("/tasks/{taskId}/children")
    public ApiResponse<java.util.List<TaskView>> children(Principal principal, @PathVariable long taskId) {
        return ApiResponse.ok(taskService.children(currentUserid(principal), taskId));
    }

    @PutMapping("/tasks/{taskId}")
    public ApiResponse<TaskView> update(
            Principal principal,
            @PathVariable long taskId,
            @Valid @RequestBody SaveTaskRequest request) {
        return ApiResponse.ok(taskService.update(currentUserid(principal), taskId, request));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ApiResponse<Void> complete(Principal principal, @PathVariable long taskId) {
        taskService.complete(currentUserid(principal), taskId, true);
        return ApiResponse.ok(null);
    }

    @PostMapping("/tasks/{taskId}/reopen")
    public ApiResponse<Void> reopen(Principal principal, @PathVariable long taskId) {
        taskService.complete(currentUserid(principal), taskId, false);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> delete(Principal principal, @PathVariable long taskId) {
        taskService.delete(currentUserid(principal), taskId);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
