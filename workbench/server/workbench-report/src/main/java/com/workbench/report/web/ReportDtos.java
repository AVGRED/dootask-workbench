package com.workbench.report.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 日报/周报接口的请求与响应模型。 */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record SaveDailyReportRequest(
            @NotBlank(message = "请填写今日完成") @Size(max = 5000) String contentDone,
            @Size(max = 5000) String contentBlocker,
            @Size(max = 5000) String contentPlan,
            Integer minutes
    ) {
    }

    public record DailyReportView(
            long id,
            LocalDate reportDate,
            String contentDone,
            String contentBlocker,
            String contentPlan,
            Integer minutes,
            int status,
            String statusText
    ) {
    }

    public record WeeklySummaryView(
            long id,
            LocalDate weekStart,
            LocalDate weekEnd,
            int reportCount,
            int missingCount,
            int totalMinutes,
            String content
    ) {
    }

    public record RegenerateRequest(LocalDate weekStart) {
    }
}
