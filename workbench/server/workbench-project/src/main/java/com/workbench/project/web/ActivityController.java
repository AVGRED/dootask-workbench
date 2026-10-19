package com.workbench.project.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.PageResult;
import com.workbench.project.service.ActivityService;
import com.workbench.project.web.ActivityDtos.ActivityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<PageResult<ActivityView>> page(
            @PathVariable long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long taskId) {
        return ApiResponse.ok(activityService.page(projectId, taskId, page, size));
    }
}
