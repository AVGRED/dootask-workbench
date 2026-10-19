# 工具链准备（板块 1 验收前置）

当前机器状态：Windows 有 JDK **17**（无 Maven）；WSL 无 JDK、无 Maven。项目要求 **JDK 21 + Maven 3.9+**。

## 方案 A：WSL 内安装（推荐，后续都在 Linux 跑）

```bash
wsl -d UbuntuTest

# JDK 21
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk maven

java -version    # 期望 21.x
mvn -v           # 期望 3.9+
```

## 方案 B：Windows 安装

1. 安装 JDK 21（Temurin 或 Microsoft OpenJDK），设置 `JAVA_HOME`；
2. 安装 Maven 3.9.x，解压后把 `bin` 加入 `PATH`；
3. 验证：

```powershell
java -version
mvn -v
```

## 生成 Maven Wrapper（建议做一次）

```bash
cd dootask/workbench/server
mvn -N wrapper:wrapper -Dmaven=3.9.9
./mvnw -v
```

之后 CI 与开发者都用 `./mvnw`，避免版本漂移。

## 验收命令

```bash
cd dootask/workbench/server
./mvnw -q verify
```

首次会下载依赖，需要几分钟。

## 前端工具链

```bash
node -v    # 需要 20+
pnpm -v    # 未安装则：corepack enable && corepack prepare pnpm@9 --activate
```

## 已知阻塞

- `workbench-auth` 的 `AuthService` 依赖 `users` 表与 BCrypt 密码格式，验收前需要：
  1. `V1__baseline.sql` 建出 `users` 表；
  2. 一个测试用账号（BCrypt 加密的密码）；
  3. `AuthControllerTest`（MockMvc）覆盖成功与失败两种路径。
- 截止目前 `mvn verify` 从未执行过，工程是否可编译未经验证；先跑一次再补业务代码。
