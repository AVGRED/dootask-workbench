import fs from "node:fs";
import path from "node:path";

const root = path.resolve(new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1"));
const composePath = path.join(root, "docker-compose.yml");
const envPath = path.join(root, ".env.example");
const nginxPath = path.join(root, "web", "nginx.conf");
const serverDockerfile = path.join(root, "server", "Dockerfile");
const webDockerfile = path.join(root, "web", "Dockerfile");

const failures = [];
let checks = 0;

function check(name, condition, detail = "") {
  checks++;
  if (condition) {
    console.log(`  [PASS] ${name}`);
  } else {
    failures.push(`${name} ${detail}`);
    console.log(`  [FAIL] ${name} ${detail}`);
  }
}

const compose = fs.readFileSync(composePath, "utf8");
const env = fs.readFileSync(envPath, "utf8");
const nginx = fs.readFileSync(nginxPath, "utf8");

check("compose 声明 mysql/server/web", ["mysql:", "server:", "web:"].every((s) => compose.includes(`\n  ${s}`)));
check("mysql 有健康检查", compose.includes("healthcheck:"));
check("server 依赖 mysql healthy", compose.includes("condition: service_healthy"));
check("mysql 数据卷持久化", compose.includes("mysql-data:/var/lib/mysql"));
check("附件目录挂载", compose.includes("uploads:/app/data/uploads"));

const vars = [...compose.matchAll(/\$\{([A-Z_]+)(?::-[^}]*)?\}/g)].map((m) => m[1]);
const missing = [...new Set(vars)].filter((name) => !env.includes(`${name}=`));
check("compose 变量都在 .env.example", missing.length === 0, missing.join(","));

check("nginx 监听三端", ["listen 8080", "listen 8081", "listen 8082"].every((s) => nginx.includes(s)));
check("nginx 反代到 server:9000", nginx.includes("server server:9000"));
check("nginx 有 SPA 回退", (nginx.match(/try_files \$uri \$uri\/ \/index\.html/g) || []).length === 3);
check("nginx 开启 gzip", nginx.includes("gzip on"));
check("nginx 限制上传体积", nginx.includes("client_max_body_size 25m"));

const serverDf = fs.readFileSync(serverDockerfile, "utf8");
check("server Dockerfile 多阶段", serverDf.includes("AS build") && serverDf.includes("eclipse-temurin:21-jre"));
check("server 非 root 运行", serverDf.includes("USER workbench"));
check("server 健康检查", serverDf.includes("HEALTHCHECK") && serverDf.includes("/actuator/health"));

const webDf = fs.readFileSync(webDockerfile, "utf8");
check("web Dockerfile 构建三端", ["@workbench/staff build", "@workbench/admin build", "@workbench/mobile build"].every((s) => webDf.includes(s)));
check("web 使用 nginx 托管", webDf.includes("nginx:1.27-alpine"));

console.log("");
if (failures.length === 0) {
  console.log(`RESULT: PASS  (${checks} checks)`);
  process.exit(0);
} else {
  console.log(`RESULT: FAIL  (${failures.length}/${checks} checks failed)`);
  process.exit(1);
}
