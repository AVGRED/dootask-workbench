package com.workbench.user.web;

import com.workbench.common.api.ApiResponse;
import com.workbench.common.api.BusinessException;
import com.workbench.common.api.PageResult;
import com.workbench.user.service.UserService;
import com.workbench.user.web.SystemDtos.ChangeRoleRequest;
import com.workbench.user.web.UserDtos.CreateUserRequest;
import com.workbench.user.web.UserDtos.UpdateUserRequest;
import com.workbench.user.web.UserDtos.UserView;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResult<UserView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userService.page(page, size, keyword));
    }

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{userid}")
    public ApiResponse<UserView> update(@PathVariable long userid, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.update(userid, request));
    }

    @DeleteMapping("/{userid}")
    public ApiResponse<Void> delete(@PathVariable long userid) {
        userService.delete(userid);
        return ApiResponse.ok(null);
    }

    /** 修改角色：需要管理员；系统还没有管理员时允许首个用户自举为 admin。 */
    @PutMapping("/{userid}/role")
    public ApiResponse<UserView> changeRole(
            Principal principal,
            @PathVariable long userid,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ApiResponse.ok(userService.changeRole(currentUserid(principal), userid, request.role()));
    }

    private long currentUserid(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(401, "未登录");
        }
        return Long.parseLong(principal.getName());
    }
}
