package com.workbench.planning.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.planning.service.TaskScheduleService;
import com.workbench.planning.web.ScheduleDtos.PreviewRequest;
import com.workbench.planning.web.ScheduleDtos.PreviewResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/schedule")
public class ScheduleController {

    private final TaskScheduleService scheduleService;

    public ScheduleController(TaskScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 只读预演：返回会被移动的任务与前后时间，不修改数据库。 */
    @PostMapping("/preview")
    public ApiResponse<PreviewResponse> preview(
            @PathVariable long projectId,
            @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(scheduleService.preview(projectId, request));
    }
}
