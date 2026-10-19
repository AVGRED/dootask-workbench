package com.workbench.user.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户接口的请求/响应模型。
 */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
            @NotBlank(message = "请输入昵称") @Size(max = 32) String nickname,
            @Size(max = 255) String profession,
            @Size(max = 255) String userimg,
            @NotBlank(message = "请输入初始密码") @Size(min = 6, max = 64) String password
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "请输入昵称") @Size(max = 32) String nickname,
            @Size(max = 255) String profession,
            @Size(max = 255) String userimg
    ) {
    }

    public record UserView(
            long userid,
            String email,
            String nickname,
            String profession,
            String userimg,
            int loginNum
    ) {
    }
}
