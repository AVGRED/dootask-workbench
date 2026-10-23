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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       SystemAdmin systemAdmin,
                       JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.systemAdmin = systemAdmin;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<UserView> page(int page, int size, String keyword) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().orderByDesc(User::getUserid);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query
                    .like(User::getAccount, keyword)
                    .or()
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
        String account = resolveAccount(request.account(), request.email());
        Long accountExists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (accountExists != null && accountExists > 0) {
            throw new BusinessException("账号已被使用");
        }
        User user = new User();
        user.setAccount(account);
        Long numericId = numericAccount(account);
        long targetId = numericId != null ? numericId : nextNonStaffId();
        if (numericId != null) {
            user.setUserid(numericId);
            User occupied = userMapper.selectById(numericId);
            if (occupied != null) {
                String reason = occupied.getDeletedAt() != null
                        ? "该工号曾被删除、仍被占用，请换一个工号"
                        : "账号已被使用";
                throw new BusinessException(reason);
            }
        }
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
        // 统一用显式 id 写入：数字账号 = 工号，非数字账号 = 1000000+ 段
        try {
            jdbcTemplate.update(
                    "insert into users (userid, account, email, nickname, profession, userimg, password, encrypt, "
                            + "role, login_num, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                    targetId, user.getAccount(), user.getEmail(), user.getNickname(), user.getProfession(),
                    user.getUserimg(), user.getPassword(), user.getEncrypt(), user.getRole(), user.getLoginNum());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("该工号已被占用，请换一个工号");
        }
        return toView(userMapper.selectById(targetId));
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

    /** 管理员重置某用户密码：重新 bcrypt 哈希。注意：已签发令牌仍有效直到过期。 */
    public void changePassword(long callerUserid, long userid, String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("密码至少 8 位");
        }
        User user = require(userid);
        boolean targetIsAdmin = SystemAdmin.ROLE_ADMIN.equals(user.getRole()) || systemAdmin.isSuper(userid);
        if (targetIsAdmin && !systemAdmin.isSuper(callerUserid)) {
            throw new BusinessException(403, "只有超级管理员能重置管理员密码");
        }
        // 重置后旧登录态失效，必须重新登录
        if (user.getTokenVersion() == null) {
            user.setTokenVersion(0);
        }
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setPassword(passwordEncoder.encode(password));
        user.setEncrypt("");
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
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
        if (systemAdmin.isSuper(userid)) {
            throw new BusinessException("超级管理员不能被降级");
        }
        boolean allowed = callerUserid == userid && systemAdmin.allowBootstrap(callerUserid, userid, role);
        if (!allowed) {
            systemAdmin.requireAdmin(callerUserid);
        }
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toView(user);
    }

    /** 账号为空时用工号邮箱的 @ 前缀兜底。 */
    private String resolveAccount(String requested, String email) {
        String account = requested == null ? "" : requested.trim();
        if (account.isEmpty()) {
            int at = email.indexOf('@');
            account = at > 0 ? email.substring(0, at) : email;
        }
        if (account.length() > 64) {
            throw new BusinessException("账号最多 64 个字符");
        }
        return account;
    }

    /** 非数字账号（邮箱/昵称注册）用 1000000+ 段，避免占用 401xxx 工号段。 */
    private long nextNonStaffId() {
        Long next = jdbcTemplate.queryForObject(
                "select coalesce(max(userid), 1000000) + 1 from users where userid >= 1000000", Long.class);
        return next == null ? 1000001L : next;
    }

    /** 纯数字账号直接当 userid 用，保证 ID 与工号一致。 */
    private Long numericAccount(String account) {
        if (account == null || !account.matches("\\d{4,18}")) {
            return null;
        }
        return Long.parseLong(account);
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
                user.getAccount(),
                user.getEmail(),
                user.getNickname(),
                user.getProfession(),
                user.getUserimg(),
                user.getLoginNum() == null ? 0 : user.getLoginNum(),
                user.getRole() == null ? SystemAdmin.ROLE_STAFF : user.getRole(),
                user.getSuperAdmin() == null ? 0 : user.getSuperAdmin());
    }
}
