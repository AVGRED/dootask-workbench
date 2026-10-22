package com.workbench.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.user.entity.User;
import com.workbench.user.mapper.UserMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 管理员校验。
 *
 * <p>首个管理员的引导规则：系统里还没有任何 admin 时，允许用户把自己提升为 admin，
 * 否则管理员角色永远无法产生（新装系统即死锁）。一旦存在 admin，后续必须由 admin 操作。</p>
 */
@Component
public class SystemAdmin {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_STAFF = "staff";

    private final UserMapper userMapper;

    public SystemAdmin(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User requireUser(long userid) {
        User user = userMapper.selectById(userid);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    public String roleOf(long userid) {
        User user = userMapper.selectById(userid);
        return user == null || user.getRole() == null ? ROLE_STAFF : user.getRole();
    }

    public boolean isAdmin(long userid) {
        return ROLE_ADMIN.equals(roleOf(userid));
    }

    /** 超级管理员：role=admin 且 super_admin=1，唯一且不可被降级/删除。 */
    public boolean isSuper(long userid) {
        User user = userMapper.selectById(userid);
        return user != null
                && ROLE_ADMIN.equals(user.getRole())
                && user.getSuperAdmin() != null
                && user.getSuperAdmin() == 1;
    }

    public void requireSuper(long userid) {
        if (!isSuper(userid)) {
            throw new BusinessException(403, "需要超级管理员权限");
        }
    }

    public void requireAdmin(long userid) {
        if (!isAdmin(userid)) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    public boolean adminExists() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, ROLE_ADMIN));
        return count != null && count > 0;
    }

    /** 允许自举：无 admin 时，用户把自己设为 admin。 */
    public boolean allowBootstrap(long callerUserid, long targetUserid, String role) {
        return Objects.equals(callerUserid, targetUserid)
                && ROLE_ADMIN.equals(role)
                && !adminExists();
    }
}
