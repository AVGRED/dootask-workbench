package com.workbench.planning.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 任务模板接口的请求/响应模型。 */
public final class TemplateDtos {

    private TemplateDtos() {
    }

    public record SaveTemplateRequest(
            @NotBlank(message = "请输入模板名称") @Size(max = 200) String name,
            @Size(max = 10000) String content,
            @Size(max = 100) String milestoneName
    ) {
    }

    public record TemplateView(
            long id,
            String name,
            String content,
            String milestoneName,
            int revisionNo,
            long revisionCount,
            LocalDateTime updatedAt
    ) {
    }

    public record RevisionView(
            long id,
            int revisionNo,
            String snapshot,
            LocalDateTime createdAt
    ) {
    }

    public record ApplyResult(long taskId, String taskName) {
    }
}
