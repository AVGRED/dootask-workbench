package com.workbench.app.settings;

import com.workbench.common.api.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 员工自助设置：改昵称/邮箱/职位、改密码。
 *
 * <p>密码兼容历史 md5（含 salt）与 BCrypt；改完统一用 BCrypt 存。</p>
 */
@Service
public class MeSettingsService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public MeSettingsService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> profile(long userid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select userid, email, nickname, coalesce(profession, '') as profession, "
                        + "coalesce(role, 'staff') as role, coalesce(avatar, '') as avatar "
                        + "from users where userid = ? and deleted_at is null",
                userid);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "用户不存在");
        }
        return rows.get(0);
    }

    public Map<String, Object> updateProfile(long userid, String nickname, String email, String profession) {
        String safeNickname = nickname == null ? "" : nickname.trim();
        String safeEmail = email == null ? "" : email.trim();
        if (safeNickname.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }
        if (safeNickname.length() > 50) {
            throw new BusinessException("昵称最多 50 字");
        }
        if (!EMAIL.matcher(safeEmail).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
        Long used = jdbc.queryForObject(
                "select count(*) from users where email = ? and userid <> ? and deleted_at is null",
                Long.class, safeEmail, userid);
        if (used != null && used > 0) {
            throw new BusinessException("邮箱已被使用");
        }
        jdbc.update(
                "update users set nickname = ?, email = ?, profession = ?, updated_at = now() "
                        + "where userid = ? and deleted_at is null",
                safeNickname, safeEmail, profession == null ? "" : profession.trim(), userid);
        return profile(userid);
    }

    public void changePassword(long userid, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BusinessException("请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("新密码至少 8 位");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select password, coalesce(encrypt, '') as encrypt from users where userid = ? and deleted_at is null",
                userid);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "用户不存在");
        }
        String stored = (String) rows.get(0).get("password");
        String salt = (String) rows.get(0).get("encrypt");
        if (!matches(oldPassword, stored, salt)) {
            throw new BusinessException("原密码不正确");
        }
        jdbc.update(
                "update users set password = ?, encrypt = '', changepass = 0, updated_at = now() where userid = ?",
                passwordEncoder.encode(newPassword), userid);
    }

    private boolean matches(String raw, String stored, String salt) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(raw, stored);
        }
        return digest(raw + (salt == null ? "" : salt)).equalsIgnoreCase(stored);
    }

    private String digest(String value) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new BusinessException("密码校验失败");
        }
    }
}
