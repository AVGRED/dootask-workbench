package com.workbench.task.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.task.service.BoardService;
import com.workbench.task.web.BoardDtos.BoardView;
import com.workbench.task.web.BoardDtos.ColumnView;
import com.workbench.task.web.BoardDtos.MoveTaskRequest;
import com.workbench.task.web.BoardDtos.SaveColumnRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/projects/{projectId}/board")
    public ApiResponse<BoardView> board(Principal principal, @PathVariable long projectId) {
        return ApiResponse.ok(boardService.board(projectId, currentUserid(principal)));
    }

    @PostMapping("/projects/{projectId}/columns")
    public ApiResponse<ColumnView> createColumn(
            Principal principal,
            @PathVariable long projectId,
            @Valid @RequestBody SaveColumnRequest request) {
        return ApiResponse.ok(boardService.createColumn(projectId, currentUserid(principal), request.name()));
    }

    @PutMapping("/projects/{projectId}/columns/{columnId}")
    public ApiResponse<ColumnView> renameColumn(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long columnId,
            @Valid @RequestBody SaveColumnRequest request) {
        return ApiResponse.ok(boardService.renameColumn(projectId, currentUserid(principal), columnId,
                request.name()));
    }

    @DeleteMapping("/projects/{projectId}/columns/{columnId}")
    public ApiResponse<Void> deleteColumn(
            Principal principal,
            @PathVariable long projectId,
            @PathVariable long columnId) {
        boardService.deleteColumn(projectId, currentUserid(principal), columnId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/tasks/{taskId}/move")
    public ApiResponse<Void> moveTask(
            Principal principal,
            @PathVariable long taskId,
            @RequestBody MoveTaskRequest request) {
        boardService.moveTask(currentUserid(principal), taskId, request.columnId());
        return ApiResponse.ok(null);
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
