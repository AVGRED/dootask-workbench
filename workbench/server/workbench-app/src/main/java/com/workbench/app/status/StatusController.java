package com.workbench.app.status;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private final StatusService statusService;

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/me/status")
    public ApiResponse<Map<String, Object>> myStatus(Principal principal) {
        return ApiResponse.ok(statusService.myStatus(currentUserid(principal)));
    }

    @PutMapping("/me/status")
    public ApiResponse<Map<String, Object>> setStatus(Principal principal,
                                                      @RequestBody SaveStatusRequest request) {
        return ApiResponse.ok(statusService.setStatus(currentUserid(principal), request.status(), request.note()));
    }

    @PutMapping("/admin/status/{userid}")
    public ApiResponse<Map<String, Object>> setStatusFor(Principal principal,
                                                         @PathVariable long userid,
                                                         @RequestBody SaveStatusRequest request) {
        return ApiResponse.ok(statusService.setStatusFor(currentUserid(principal), userid, request.status(), request.note()));
    }

    @GetMapping("/team/status")
    public ApiResponse<List<Map<String, Object>>> team(Principal principal) {
        return ApiResponse.ok(statusService.team(currentUserid(principal)));
    }

    public record SaveStatusRequest(@NotBlank(message = "请选择状态") String status, String note) {
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
