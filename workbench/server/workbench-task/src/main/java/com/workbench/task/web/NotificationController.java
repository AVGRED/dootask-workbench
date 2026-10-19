package com.workbench.task.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.task.service.NotificationService;
import com.workbench.task.web.CommentDtos.NotificationView;
import com.workbench.task.web.CommentDtos.UnreadCountView;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationView>> list(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.ok(notificationService.list(currentUserid(principal), unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount(Principal principal) {
        return ApiResponse.ok(new UnreadCountView(notificationService.unreadCount(currentUserid(principal))));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(Principal principal, @PathVariable long id) {
        notificationService.markRead(currentUserid(principal), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<UnreadCountView> markAllRead(Principal principal) {
        return ApiResponse.ok(new UnreadCountView(notificationService.markAllRead(currentUserid(principal))));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
