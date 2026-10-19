package com.workbench.worklog.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 工时接口的请求/响应模型。 */
public final class WorklogDtos {

    private WorklogDtos() {
    }

    public record CreateWorklogRequest(
            @NotBlank(message = "缺少请求号") @Size(max = 64) String requestId,
            @NotNull(message = "请填写分钟数") @Min(value = 1, message = "工时至少 1 分钟") @Max(value = 1440, message = "单条工时不能超过 24 小时") Integer minutes,
            @NotNull(message = "请选择日期") LocalDate workDate,
            @Size(max = 500) String note
    ) {
    }

    public record WorklogView(
            long id,
            long taskId,
            long userid,
            int minutes,
            LocalDate workDate,
            String note
    ) {
    }

    public record CapacityMember(
            long userid,
            long loggedMinutes,
            long availableMinutes,
            long utilizationPercent,
            long overrunMinutes,
            long taskCount
    ) {
    }

    public record CapacitySummary(
            LocalDate from,
            LocalDate to,
            int workdays,
            int dailyMinutes,
            List<CapacityMember> members
    ) {
    }
}
