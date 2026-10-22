package com.workbench.auth.web;

import com.workbench.auth.service.AuthService;
import com.workbench.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/logout")
    public com.workbench.common.api.ApiResponse<Void> logout(java.security.Principal principal) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            authService.logout(Long.parseLong(principal.getName()));
        }
        return com.workbench.common.api.ApiResponse.ok(null);
    }

    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, clientIp(httpRequest)));
    }
}
