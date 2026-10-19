package com.workbench.task.web;

import java.time.LocalDateTime;

/** 附件接口的响应模型。 */
public final class AttachmentDtos {

    private AttachmentDtos() {
    }

    public record AttachmentView(
            long id,
            long taskId,
            long userid,
            String originalName,
            String mimeType,
            long sizeBytes,
            LocalDateTime createdAt
    ) {
    }
}
