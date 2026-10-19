package com.workbench.planning.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 里程碑接口的请求/响应模型。 */
public final class MilestoneDtos {

    private MilestoneDtos() {
    }

    public record SaveMilestoneRequest(
            @NotBlank(message = "请输入里程碑名称") @Size(max = 100) String name,
            @Size(max = 500) String description,
            Long ownerUserid,
            LocalDate targetAt,
            @Pattern(regexp = "open|completed", message = "状态只能是 open 或 completed") String status,
            @Min(value = -1, message = "提醒天数最小为 -1") @Max(value = 30, message = "提醒天数最大为 30") Integer reminderDays
    ) {
    }

    public record MilestoneView(
            long id,
            String name,
            String description,
            long ownerUserid,
            LocalDate targetAt,
            String status,
            int reminderDays,
            int sort,
            long taskCount,
            long completedCount
    ) {
    }
}
