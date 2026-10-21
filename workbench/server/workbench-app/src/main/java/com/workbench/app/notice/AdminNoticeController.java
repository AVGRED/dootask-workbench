package com.workbench.app.notice;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    private final AdminNoticeService service;

    public AdminNoticeController(AdminNoticeService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> recent(Principal principal) {
        service.recent();
        return ApiResponse.ok(service.recent());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> publish(Principal principal, @RequestBody PublishRequest request) {
        return ApiResponse.ok(service.publish(currentUserid(principal), request.title(), request.content()));
    }

    public record PublishRequest(String title, String content) {
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
