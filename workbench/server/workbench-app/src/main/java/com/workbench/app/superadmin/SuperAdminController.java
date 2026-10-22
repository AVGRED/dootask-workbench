package com.workbench.app.superadmin;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 超级管理员的"更高权限"操作入口，全部在 service 层做 requireSuper 校验。 */
@RestController
@RequestMapping("/api/v1/super")
public class SuperAdminController {

    private final SuperAdminService service;

    public SuperAdminController(SuperAdminService service) {
        this.service = service;
    }

    @PostMapping("/reports")
    public ApiResponse<Map<String, Object>> writeReport(Principal principal, @RequestBody ReportRequest request) {
        return ApiResponse.ok(service.writeReport(currentUserid(principal), request.userid(), request.reportDate(),
                request.done(), request.blocker(), request.plan(), request.minutes()));
    }

    @PostMapping("/todos")
    public ApiResponse<Map<String, Object>> addTodo(Principal principal, @RequestBody TodoRequest request) {
        return ApiResponse.ok(service.addTodo(currentUserid(principal), request.userid(), request.title()));
    }

    @PostMapping("/users/{userid}/force-logout")
    public ApiResponse<Map<String, Object>> forceLogout(Principal principal, @PathVariable long userid) {
        return ApiResponse.ok(service.forceLogout(currentUserid(principal), userid));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> deleteTask(Principal principal, @PathVariable long taskId) {
        return ApiResponse.ok(service.deleteTask(currentUserid(principal), taskId));
    }

    @DeleteMapping("/projects/{projectId}")
    public ApiResponse<Map<String, Object>> deleteProject(Principal principal, @PathVariable long projectId) {
        return ApiResponse.ok(service.deleteProject(currentUserid(principal), projectId));
    }

    @GetMapping("/audit")
    public ApiResponse<List<Map<String, Object>>> auditLogs(Principal principal,
                                                            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.auditLogs(currentUserid(principal), limit));
    }

    public record ReportRequest(long userid, LocalDate reportDate, String done, String blocker, String plan,
                                Integer minutes) {
    }

    public record TodoRequest(long userid, String title) {
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
