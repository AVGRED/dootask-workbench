package com.workbench.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 任务接口的请求/响应模型。 */
public final class TaskDtos {

    private TaskDtos() {
    }

    public record SaveTaskRequest(
            @NotBlank(message = "请输入任务标题") @Size(max = 255) String name,
            @Size(max = 500) String desc,
            Long ownerUserid,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer pLevel
    ) {
    }

    public record TaskView(
            long id,
            long projectId,
            String name,
            String desc,
            long ownerUserid,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime completeAt,
            int pLevel,
            boolean completed
    ) {
    }
}
