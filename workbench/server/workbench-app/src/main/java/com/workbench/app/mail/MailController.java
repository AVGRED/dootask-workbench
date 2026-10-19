package com.workbench.app.mail;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.common.mail.MailService;
import com.workbench.user.service.SystemAdmin;
import java.security.Principal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 邮件通道自检：返回当前模式（smtp/disabled）与是否投递成功。 */
@RestController
@RequestMapping("/api/v1/admin/mail")
public class MailController {

    private final MailService mailService;
    private final SystemAdmin systemAdmin;

    public MailController(MailService mailService, SystemAdmin systemAdmin) {
        this.mailService = mailService;
        this.systemAdmin = systemAdmin;
    }

    @PostMapping("/test")
    public ApiResponse<MailTestResult> test(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        systemAdmin.requireAdmin(Long.parseLong(principal.getName()));
        boolean sent = mailService.send(principal.getName() + "@workbench.local",
                "工作台邮件通道自检", "这是一封自检邮件；收到说明 SMTP 配置正确。");
        return ApiResponse.ok(new MailTestResult(sent, mailService.mode()));
    }

    public record MailTestResult(boolean sent, String mode) {
    }
}
