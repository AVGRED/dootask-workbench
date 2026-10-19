package com.workbench.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbench.common.api.BusinessException;
import com.workbench.common.api.PageResult;
import com.workbench.user.entity.User;
import com.workbench.user.mapper.UserMapper;
import com.workbench.user.web.UserDtos.CreateUserRequest;
import com.workbench.user.web.UserDtos.UpdateUserRequest;
import com.workbench.user.web.UserDtos.UserView;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户管理：分页查询、创建、修改与软删除。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SystemAdmin systemAdmin;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, SystemAdmin systemAdmin) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.systemAdmin = systemAdmin;
    }

    public PageResult<UserView> page(int page, int size, String keyword) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().orderByDesc(User::getUserid);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query
                    .like(User::getEmail, keyword)
                    .or()
                    .like(User::getNickname, keyword));
        }
        Page<User> result = userMapper.selectPage(new Page<>(Math.max(page, 1), safeSize), wrapper);
        List<UserView> list = result.getRecords().stream().map(UserService::toView).toList();
        return PageResult.of(list, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    public UserView create(CreateUserRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, request.email()));
        if (exists != null && exists > 0) {
            throw new BusinessException("邮箱已被使用");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setNickname(request.nickname());
        user.setProfession(request.profession() == null ? "" : request.profession());
        user.setUserimg(request.userimg() == null ? "" : request.userimg());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEncrypt("");
        user.setRole(SystemAdmin.ROLE_STAFF);
        user.setLoginNum(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return toView(user);
    }

    public UserView update(long userid, UpdateUserRequest request) {
        User user = require(userid);
        user.setNickname(request.nickname());
        if (request.profession() != null) user.setProfession(request.profession());
        if (request.userimg() != null) user.setUserimg(request.userimg());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toView(user);
    }

    public void delete(long userid) {
        require(userid);
        userMapper.deleteById(userid);
    }

    public UserView changeRole(long callerUserid, long userid, String role) {
        if (!SystemAdmin.ROLE_ADMIN.equals(role) && !SystemAdmin.ROLE_STAFF.equals(role)) {
            throw new BusinessException("角色只能是 admin 或 staff");
        }
        User user = require(userid);
        boolean allowed = callerUserid == userid && systemAdmin.allowBootstrap(callerUserid, userid, role);
        if (!allowed) {
            systemAdmin.requireAdmin(callerUserid);
        }
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toView(user);
    }

    private User require(long userid) {
        User user = userMapper.selectById(userid);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private static UserView toView(User user) {
        return new UserView(
                user.getUserid(),
                user.getEmail(),
                user.getNickname(),
                user.getProfession(),
                user.getUserimg(),
                user.getLoginNum() == null ? 0 : user.getLoginNum(),
                user.getRole() == null ? SystemAdmin.ROLE_STAFF : user.getRole());
    }
}
