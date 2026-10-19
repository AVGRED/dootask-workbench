package com.workbench.planning.web;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** 排期预演接口的请求/响应模型。 */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record PreviewRequest(
            @NotNull(message = "请选择锚点任务") Long anchorTaskId,
            @NotNull(message = "请选择开始时间") LocalDateTime anchorStart,
            @NotNull(message = "请选择结束时间") LocalDateTime anchorEnd,
            List<Integer> workdays
    ) {
    }

    public record ChangeView(
            long taskId,
            LocalDateTime oldStart,
            LocalDateTime oldEnd,
            LocalDateTime newStart,
            LocalDateTime newEnd
    ) {
    }

    public record PreviewResponse(
            List<ChangeView> changes,
            int changeCount
    ) {
    }
}
