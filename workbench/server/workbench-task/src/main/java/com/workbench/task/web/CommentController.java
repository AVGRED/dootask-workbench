package com.workbench.task.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.task.service.CommentService;
import com.workbench.task.web.CommentDtos.CommentView;
import com.workbench.task.web.CommentDtos.CreateCommentRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/tasks/{taskId}/comments")
    public ApiResponse<List<CommentView>> list(@PathVariable long taskId) {
        return ApiResponse.ok(commentService.list(taskId));
    }

    @PostMapping("/tasks/{taskId}/comments")
    public ApiResponse<CommentView> create(
            Principal principal,
            @PathVariable long taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok(commentService.create(currentUserid(principal), taskId, request));
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> delete(Principal principal, @PathVariable long id) {
        commentService.delete(currentUserid(principal), id);
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
