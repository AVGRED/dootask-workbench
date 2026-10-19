package com.workbench.planning.web;

import jakarta.validation.constraints.NotNull;

/** 依赖接口的请求/响应模型。 */
public final class DependencyDtos {

    private DependencyDtos() {
    }

    public record AddDependencyRequest(
            @NotNull(message = "请选择前置任务") Long predecessorTaskId,
            @NotNull(message = "请选择后置任务") Long successorTaskId
    ) {
    }

    public record DependencyView(
            long id,
            long predecessorTaskId,
            long successorTaskId
    ) {
    }

    public record RiskSummaryView(
            long edgeCount,
            long blockedCount,
            long unownedCount,
            long overdueCount
    ) {
    }
}
