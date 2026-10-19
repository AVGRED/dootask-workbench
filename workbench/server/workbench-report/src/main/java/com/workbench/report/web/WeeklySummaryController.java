package com.workbench.report.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.report.service.WeeklySummaryService;
import com.workbench.report.web.ReportDtos.RegenerateRequest;
import com.workbench.report.web.ReportDtos.WeeklySummaryView;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weekly-summaries")
public class WeeklySummaryController {

    private final WeeklySummaryService weeklySummaryService;

    public WeeklySummaryController(WeeklySummaryService weeklySummaryService) {
        this.weeklySummaryService = weeklySummaryService;
    }

    @GetMapping
    public ApiResponse<List<WeeklySummaryView>> list(Principal principal) {
        return ApiResponse.ok(weeklySummaryService.list(currentUserid(principal)));
    }

    @PostMapping("/regenerate")
    public ApiResponse<WeeklySummaryView> regenerate(
            Principal principal,
            @RequestBody(required = false) RegenerateRequest request) {
        LocalDate weekStart = request == null || request.weekStart() == null
                ? LocalDate.now().minusWeeks(1)
                : request.weekStart();
        return ApiResponse.ok(weeklySummaryService.generateForUser(currentUserid(principal), weekStart));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
