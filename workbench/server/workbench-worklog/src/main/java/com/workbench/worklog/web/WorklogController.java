package com.workbench.worklog.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.worklog.service.WorklogService;
import com.workbench.worklog.web.WorklogDtos.CapacitySummary;
import com.workbench.worklog.web.WorklogDtos.CreateWorklogRequest;
import com.workbench.worklog.web.WorklogDtos.WorklogView;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorklogController {

    private final WorklogService worklogService;

    public WorklogController(WorklogService worklogService) {
        this.worklogService = worklogService;
    }

    @PostMapping("/tasks/{taskId}/worklogs")
    public ApiResponse<WorklogView> create(
            Principal principal,
            @PathVariable long taskId,
            @Valid @RequestBody CreateWorklogRequest request) {
        return ApiResponse.ok(worklogService.create(currentUserid(principal), taskId, request));
    }

    @GetMapping("/tasks/{taskId}/worklogs")
    public ApiResponse<List<WorklogView>> list(@PathVariable long taskId) {
        return ApiResponse.ok(worklogService.listByTask(taskId));
    }

    @DeleteMapping("/worklogs/{id}")
    public ApiResponse<Void> delete(Principal principal, @PathVariable long id) {
        worklogService.delete(currentUserid(principal), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/projects/{projectId}/capacity")
    public ApiResponse<CapacitySummary> capacity(
            @PathVariable long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(worklogService.capacity(projectId, from, to));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
