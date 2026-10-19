-- 清理 E2E / 压力测试遗留账号（软删除，仅匹配测试前缀）。
-- 用法：mysql -u wb -p workbench < cleanup-test-users.sql

-- 先看看会命中多少
SELECT COUNT(*) AS will_delete
FROM users
WHERE deleted_at IS NULL
  AND (email LIKE 'e2e-%' OR email LIKE 'viewer-%' OR email LIKE 'x%@workbench.local'
       OR email LIKE 'batch-del-test%@workbench.local');

-- 真正执行
UPDATE users
SET deleted_at = now()
WHERE deleted_at IS NULL
  AND (email LIKE 'e2e-%' OR email LIKE 'viewer-%' OR email LIKE 'x%@workbench.local'
       OR email LIKE 'batch-del-test%@workbench.local');
