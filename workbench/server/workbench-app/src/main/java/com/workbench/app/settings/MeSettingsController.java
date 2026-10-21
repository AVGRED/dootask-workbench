package com.workbench.app.settings;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeSettingsController {

    private final MeSettingsService service;

    public MeSettingsController(MeSettingsService service) {
        this.service = service;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(Principal principal) {
        return ApiResponse.ok(service.profile(currentUserid(principal)));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(Principal principal,
                                                          @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(service.updateProfile(
                currentUserid(principal), request.nickname(), request.email(), request.profession()));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(Principal principal, @RequestBody ChangePasswordRequest request) {
        service.changePassword(currentUserid(principal), request.oldPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }

    public record UpdateProfileRequest(String nickname, String email, String profession) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
