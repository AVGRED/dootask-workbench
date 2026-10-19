package com.workbench.report.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 日报 / 周报 / 项目报表的请求与响应模型。 */
public final class ReportDtos {

    private ReportDtos() {
    }

    // ---- 日报 ----

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

    // ---- 周报 ----

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

    // ---- 项目报表 ----

    public record DayPoint(LocalDate date, long minutes, long completedTasks) {
    }

    public record MemberStat(long userid, String email, long minutes, long taskCount) {
    }

    public record ProjectReport(
            long projectId,
            LocalDate from,
            LocalDate to,
            long taskTotal,
            long taskCompleted,
            long taskOverdue,
            long completionPercent,
            long worklogMinutes,
            long milestoneTotal,
            long milestoneDone,
            List<MemberStat> members,
            List<DayPoint> days
    ) {
    }
}
