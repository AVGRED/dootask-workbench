import type { ApiResponse } from "./types";

export interface ClientOptions {
  baseUrl: string;
  getToken?: () => string | null;
}

export class ApiError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * 解析响应体：dev server 代理不可达、或后端返回非 JSON 时，
 * 直接抛带状态码的可读错误，而不是让 `response.json()` 抛出
 * "Unexpected end of JSON input" 这类对用户无意义的异常。
 */
export async function parsePayload<T>(response: Response): Promise<ApiResponse<T>> {
  const text = await response.text();
  if (!text) {
    throw new ApiError(response.status || 500, `服务无响应（HTTP ${response.status}），请确认后端已启动`);
  }
  try {
    return JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new ApiError(response.status || 500, `响应不是合法 JSON（HTTP ${response.status}）：${text.slice(0, 120)}`);
  }
}

/** 令牌失效时清空本地登录态，并广播事件让各应用跳转登录页。 */
export function handleUnauthorized(response: Response, code?: number): void {
  if ((response.status !== 401 && code !== 401) || typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem("workbench.token");
  window.localStorage.removeItem("workbench.user");
  window.localStorage.removeItem("workbench.admin.token");
  window.localStorage.removeItem("workbench.admin.user");
  window.dispatchEvent(new CustomEvent("workbench-token-expired"));
}

export function buildHeaders(options: ClientOptions, json = true): Record<string, string> {
  const headers: Record<string, string> = {};
  if (json) headers["Content-Type"] = "application/json";
  const token = options.getToken?.();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

/** 统一的 JSON 请求：拼 header、解析、处理 401、抛业务错误。 */
export async function requestJson<T>(
  options: ClientOptions,
  method: string,
  path: string,
  body?: unknown
): Promise<T> {
  const response = await fetch(`${options.baseUrl}${path}`, {
    method,
    headers: buildHeaders(options),
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const payload = await parsePayload<T>(response);
  handleUnauthorized(response, payload.code);
  if (payload.code !== 0) {
    throw new ApiError(payload.code, payload.message);
  }
  return payload.data;
}
