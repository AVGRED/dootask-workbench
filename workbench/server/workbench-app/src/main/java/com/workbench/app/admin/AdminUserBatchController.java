package com.workbench.app.admin;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.user.service.SystemAdmin;
import jakarta.validation.constraints.NotEmpty;
import java.security.Principal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端用户批量操作（目前支持软删除）。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserBatchController {

    private final JdbcTemplate jdbc;
    private final SystemAdmin systemAdmin;

    public AdminUserBatchController(JdbcTemplate jdbc, SystemAdmin systemAdmin) {
        this.jdbc = jdbc;
        this.systemAdmin = systemAdmin;
    }

    @PostMapping("/batch")
    public ApiResponse<BatchResult> batch(Principal principal, @RequestBody BatchUsersRequest request) {
        long operator = currentUserid(principal);
        systemAdmin.requireAdmin(operator);
        if (request.userids() == null || request.userids().isEmpty()) {
            throw new BusinessException("请选择用户");
        }
        if (!"delete".equals(request.action())) {
            throw new BusinessException("不支持的操作：" + request.action());
        }
        int affected = 0;
        for (Long userid : request.userids()) {
            if (userid == null || userid == operator) {
                continue;
            }
            Long adminCount = jdbc.queryForObject(
                    "select count(*) from users where role = 'admin' and deleted_at is null", Long.class);
            List<String> roles = jdbc.queryForList(
                    "select role from users where userid = ? and deleted_at is null", String.class, userid);
            if (roles.isEmpty()) {
                continue;
            }
            boolean isTargetAdmin = "admin".equals(roles.get(0));
            if (isTargetAdmin && adminCount != null && adminCount <= 1) {
                throw new BusinessException("不能删除最后一个管理员");
            }
            affected += jdbc.update("update users set deleted_at = now() where userid = ? and deleted_at is null", userid);
        }
        return ApiResponse.ok(new BatchResult(affected, "delete"));
    }

    public record BatchUsersRequest(String action, List<Long> userids) {
    }

    public record BatchResult(int affected, String action) {
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
