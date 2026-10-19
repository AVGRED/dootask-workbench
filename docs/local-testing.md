# 本地测试与 CI 的差异

本文说明如何在本地跑完整测试，以及本地环境与 GitHub Actions 的差异。它不替代[发布验收清单](release-acceptance-checklist.md)。

## 一键运行

```bash
# 单元测试（不需要 Redis，也不需要授权库）
bash scripts/release-run-unit-tests-isolated.sh Unit

# 完整 Feature 套件（需要 redis-server 与 gcc）
bash scripts/release-run-unit-tests-isolated.sh Feature

# 只跑一个测试类
bash scripts/release-run-unit-tests-isolated.sh Feature --filter ProjectTaskScheduleFeatureTest
```

脚本会在 `/tmp` 下创建一次性 MariaDB（默认 `13311`）与 Redis（默认 `16380`），运行结束后关闭并删除；端口被占用时会拒绝启动，避免误连既有服务。可用 `DOOTASK_UNIT_TEST_DB_PORT` / `DOOTASK_UNIT_TEST_REDIS_PORT` 覆盖端口。

## 授权库（`doo.so`）

DooTask 的正常运行依赖未随仓库分发的授权 FFI 库。Feature 套件里会经过 `Doo::load()` 的用例（HTTP 中间件、模型保存钩子等）在缺少该库时无法执行。

脚本的处理顺序：

1. 若设置了 `DOOTASK_LIBRARY_PATH`，直接使用；
2. 若存在 `/usr/lib/doo/doo.so`，使用真实授权库；
3. 否则调用 `scripts/build-test-doo-so.sh` 编译一个**只有符号、没有业务逻辑**的桩库并临时注入。

桩库只保证调用链可跑通：它不校验授权、不做加密、原样返回翻译文本。因此：

- 与授权、加密、翻译相关的行为**不能**用桩库验证；
- 桩库产物在临时目录中，退出即删除，不会被部署使用；
- CI 使用带真实 `doo.so` 的项目镜像，仍以 CI 结果为准。

## 与 CI 的差异

| 维度 | 本地脚本 | GitHub Actions |
|---|---|---|
| PHP / 扩展 | 宿主机或 WSL 中的 PHP 8.4 | `kuaifan/php:swoole-8.4` 镜像 |
| 授权库 | 真实库或临时桩库 | 镜像内置的真实库 |
| 数据库 | 临时 MariaDB（空库 + 迁移） | MariaDB 服务容器 + `migrate --seed` |
| Redis | 临时实例 | `redis:alpine` 服务容器 |
| 种子数据 | 仅 `UsersTableSeeder` + `SettingsTableSeeder` | 完整 `DatabaseSeeder` |
| 用例范围 | `Unit` / `Feature` 可分别运行 | `php vendor/bin/phpunit`（全部套件） |

因此「本地全绿」只代表代码与测试自洽；发布前仍需按验收清单在真实数据库副本、干净 Linux 主机、双实例和多端环境复核。

## 已知跳过项

`AdminCreateUserTest` 中两处端到端注册用例需要 Swoole/LaravelS 常驻运行时，在 CLI 下按设计跳过（`markTestSkipped`），不计为通过。

## 组件拆分后的模板连线检查

把大组件拆成子组件时，最容易漏掉的是模板里仍指向已移走的状态或方法。ESLint 能解析模板但不解析标识符，浏览器验收又不是随时可跑，因此提供静态检查：

```bash
npm run check:vue-wiring          # 默认检查工作台的 12 个组件
node scripts/check-vue-wiring.mjs --all   # 全仓扫描（存量组件仍有 64 处待清理）
```

脚本会解析模板表达式，跟踪 `v-for` / 插槽作用域局部变量，并与组件的 `props/data/computed/methods`、全局 mixin 与 `$` 开头的实例属性对照，报告无法解析的标识符。默认只覆盖工作台组件，CI 已接入；存量组件的告警待单独清理后再扩大范围。

## 备份尚未推送的提交

当本地提交无法推送到远端时（没有写权限、凭据不可用），用 bundle 做一份可移植备份：

```bash
bash scripts/backup-local-bundle.sh            # 默认以当前分支的上游（回退 origin/pro）为基准
bash scripts/backup-local-bundle.sh origin/pro # 或显式指定基准
```

脚本会在仓库同级的 `backups/`（可用 `DOOTASK_BACKUP_DIR` 覆盖）生成 `dootask-<时间戳>.bundle`，并校验可读性。在另一台已检出同一基准提交的机器上执行 `git bundle unbundle <文件>` 即可取回这些提交。bundle 只是本地容灾，不能替代远端仓库。

设置 `DOOTASK_BACKUP_COPY_DIR` 时会把 bundle 复制到该目录并逐字节比对 SHA-256，适合放进云同步目录（例如 OneDrive）实现异地副本：

```bash
DOOTASK_BACKUP_COPY_DIR=/mnt/c/Users/<user>/OneDrive/backups bash scripts/backup-local-bundle.sh
```

### 本地裸仓库作为 git 远端

在没有可写 GitHub 仓库时，可以把一个裸仓库当作远端，至少保证分支与对象有第二份：

```powershell
git init --bare D:\backups\dootask.git
git -C D:\repos\workbench\dootask remote add local-backup D:\backups\dootask.git
git -C D:\repos\workbench\dootask push local-backup pro:refs/heads/workbench-planning
git -C D:\backups\dootask.git log --oneline -1 workbench-planning
```

恢复：`git clone --branch workbench-planning D:\backups\dootask.git <目录>`。裸仓库在同一台机器/同一块磁盘上，只防误删与分支损坏，不防磁盘故障；异地副本仍需上面的 bundle 或可写远端。
