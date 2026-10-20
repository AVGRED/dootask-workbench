import { expect, request as playwrightRequest, type APIRequestContext } from "@playwright/test";

export const DEMO = { username: "demo@workbench.local", password: "secret123" };

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

export async function login(credentials = DEMO): Promise<string> {
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
