package com.workbench.project.web;

import java.time.LocalDateTime;

/** 项目动态接口的响应模型。 */
public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record ActivityView(
            long id,
            Long taskId,
            long userid,
            String action,
            String actionText,
            String detail,
            LocalDateTime createdAt
    ) {
    }
}
