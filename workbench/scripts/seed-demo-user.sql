-- CI 浏览器验收用演示账号：密码为 md5('secret123')，首次登录会自动升级为 BCrypt。
INSERT INTO users (email, nickname, profession, password, encrypt, login_num, role, created_at, updated_at)
VALUES ('demo@workbench.local', '演示用户', '', '5d7845ac6ee7cfffafc5fe5f35cf666d', '', 0, 'staff', NOW(), NOW())
ON DUPLICATE KEY UPDATE password = VALUES(password);
