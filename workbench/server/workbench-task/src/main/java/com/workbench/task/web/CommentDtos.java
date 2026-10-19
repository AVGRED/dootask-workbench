package com.workbench.task.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 评论与通知的请求/响应模型。 */
public final class CommentDtos {

    private CommentDtos() {
    }

    public record CreateCommentRequest(
            @NotBlank(message = "请输入评论内容") @Size(max = 2000, message = "评论最多 2000 字") String content,
            List<Long> mentionUserids
    ) {
    }

    public record CommentView(
            long id,
            long taskId,
            long userid,
            String content,
            List<Long> mentionUserids,
            LocalDateTime createdAt
    ) {
    }

    public record NotificationView(
            long id,
            String type,
            String title,
            String payload,
            boolean read,
            LocalDateTime createdAt
    ) {
    }

    public record UnreadCountView(long unread) {
    }
}
