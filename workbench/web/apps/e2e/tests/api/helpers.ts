import { expect, request as playwrightRequest, type APIRequestContext } from "@playwright/test";

const staffPassword = process.env.WORKBENCH_STAFF_PASSWORD ?? "";

/** 普通员工账号（默认 401004），密码走环境变量，避免把口令写进仓库。 */
export const STAFF = {
  username: process.env.WORKBENCH_STAFF_USERNAME ?? "401008",
  password: staffPassword
};

/** 管理员账号（默认 401001）。 */
export const ADMIN = {
  username: process.env.WORKBENCH_ADMIN_USERNAME ?? "401001",
  password: process.env.WORKBENCH_ADMIN_PASSWORD ?? ""
};

/** 兼容旧引用。 */
export const DEMO = STAFF;

export type Json = { code: number; message?: string; data: any };

let shared: APIRequestContext | null = null;

export async function api(): Promise<APIRequestContext> {
  if (!shared) {
    shared = await playwrightRequest.newContext({
      baseURL: process.env.WORKBENCH_API ?? "http://127.0.0.1:9020",
      extraHTTPHeaders: { "Content-Type": "application/json" }
    });
  }
  return shared;
}

/** 发请求并返回业务响应体（HTTP 状态不参与断言，统一看 code）。 */
export async function call(
  token: string | null,
  method: "GET" | "POST" | "PUT" | "DELETE",
  path: string,
  body?: unknown
): Promise<Json> {
  const context = await api();
  const response = await context.fetch(path, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await response.text();
  expect(text, `${method} ${path} 返回空响应`).not.toBe("");
  return JSON.parse(text) as Json;
}

export async function login(credentials = STAFF): Promise<string> {
  if (!credentials.password) {
    throw new Error("WORKBENCH_STAFF_PASSWORD 未设置，无法运行 API 测试");
  }
  const result = await call(null, "POST", "/api/v1/auth/login", credentials);
  expect(result.code, `登录失败：${result.message}`).toBe(0);
  return result.data.token as string;
}

export function suffix(): string {
  return `api${Date.now()}${Math.floor(Math.random() * 1000)}`;
}

export async function createProject(token: string, name = `API 测试项目 ${suffix()}`): Promise<number> {
  const result = await call(token, "POST", "/api/v1/projects", { name, desc: "playwright api spec" });
  expect(result.code).toBe(0);
  return result.data.id as number;
}

export async function createTask(
  token: string,
  projectId: number,
  payload: Record<string, unknown> = { name: `任务 ${suffix()}` }
): Promise<number> {
  const result = await call(token, "POST", `/api/v1/projects/${projectId}/tasks`, payload);
  expect(result.code).toBe(0);
  return result.data.id as number;
}
