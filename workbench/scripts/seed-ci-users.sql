-- CI 浏览器验收账号：
--   demo@workbench.local / secret123       （staff，用于员工端 + 移动端用例）
--   admin@workbench.local / <CI 专用密码，见 .github/workflows/workbench-ci.yml>（admin，用于管理端用例）
-- 密码以 md5 写入，首次登录由后端自动升级为 BCrypt。
INSERT INTO users (email, nickname, profession, password, encrypt, login_num, role, created_at, updated_at)
VALUES
  ('demo@workbench.local', '演示用户', '', '5d7845ac6ee7cfffafc5fe5f35cf666d', '', 0, 'staff', NOW(), NOW()),
  ('admin@workbench.local', 'admin', '', '451ec341d2e35302112a5e9023fb3f45', '', 0, 'admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  role = VALUES(role);
