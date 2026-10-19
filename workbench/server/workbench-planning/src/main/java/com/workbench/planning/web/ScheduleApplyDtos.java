package com.workbench.planning.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 排期应用与撤销接口的请求/响应模型。 */
public final class ScheduleApplyDtos {

    private ScheduleApplyDtos() {
    }

    public record ApplyRequest(
            @NotBlank(message = "缺少请求号") @Size(max = 64) String requestId,
            @NotNull(message = "请选择锚点任务") Long anchorTaskId,
            @NotNull(message = "请选择开始时间") LocalDateTime anchorStart,
            @NotNull(message = "请选择结束时间") LocalDateTime anchorEnd,
            List<Integer> workdays
    ) {
    }

    public record BatchView(
            long id,
            String requestId,
            int changeCount,
            LocalDateTime createdAt,
            boolean undone
    ) {
    }
}
