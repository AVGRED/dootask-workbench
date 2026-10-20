import { expect, test } from "@playwright/test";
import { DEMO, call, login } from "./helpers";

test.describe("认证", () => {
  test("登录成功并返回 token", async () => {
    const result = await call(null, "POST", "/api/v1/auth/login", DEMO);
    expect(result.code).toBe(0);
    expect(result.data.token).toBeTruthy();
    expect(result.data.userid).toBeGreaterThan(0);
  });

  test("密码错误返回业务错误而不是异常", async () => {
    const result = await call(null, "POST", "/api/v1/auth/login", {
      username: DEMO.username,
      password: "wrong-password"
    });
    expect(result.code).not.toBe(0);
    expect(result.message).toContain("账号或密码错误");
  });

  test("未登录访问受保护接口返回 401", async () => {
    const result = await call(null, "GET", "/api/v1/projects");
    expect(result.code).toBe(401);
  });

  test("登录后可读写个人状态", async () => {
    const token = await login();
    await call(token, "PUT", "/api/v1/me/status", { status: "focus", note: "api spec" });
    const mine = await call(token, "GET", "/api/v1/me/status");
    expect(mine.data.status).toBe("focus");
    expect(mine.data.note).toBe("api spec");
    await call(token, "PUT", "/api/v1/me/status", { status: "idle", note: "" });
  });

  test("非法状态被拒绝", async () => {
    const token = await login();
    const result = await call(token, "PUT", "/api/v1/me/status", { status: "sleeping" });
    expect(result.code).toBe(400);
  });
});
