package com.workbench.planning.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.planning.service.ScheduleApplyService;
import com.workbench.planning.web.ScheduleApplyDtos.ApplyRequest;
import com.workbench.planning.web.ScheduleApplyDtos.BatchView;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ScheduleApplyController {

    private final ScheduleApplyService scheduleApplyService;

    public ScheduleApplyController(ScheduleApplyService scheduleApplyService) {
        this.scheduleApplyService = scheduleApplyService;
    }

    @PostMapping("/projects/{projectId}/schedule/apply")
    public ApiResponse<BatchView> apply(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody ApplyRequest request) {
        return ApiResponse.ok(scheduleApplyService.apply(currentUserid(principal), projectId, request));
    }

    @PostMapping("/schedule/batches/{id}/undo")
    public ApiResponse<BatchView> undo(Principal principal, @PathVariable long id) {
        return ApiResponse.ok(scheduleApplyService.undo(currentUserid(principal), id));
    }

    @GetMapping("/projects/{projectId}/schedule/batches")
    public ApiResponse<List<BatchView>> batches(@PathVariable long projectId) {
        return ApiResponse.ok(scheduleApplyService.batches(projectId));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
