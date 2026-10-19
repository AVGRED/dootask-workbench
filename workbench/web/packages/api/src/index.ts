import type { ApiResponse, LoginRequest, LoginResponse } from "./types";

export * from "./types";

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

export class ApiClient {
  constructor(private readonly options: ClientOptions) {}

  async login(payload: LoginRequest): Promise<LoginResponse> {
    return this.post<LoginResponse>("/api/v1/auth/login", payload);
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const token = this.options.getToken?.();
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${this.options.baseUrl}${path}`, {
      method: "POST",
      headers,
      body: JSON.stringify(body)
    });
    const payload = (await response.json()) as ApiResponse<T>;
    if (payload.code !== 0) throw new ApiError(payload.code, payload.message);
    return payload.data;
  }
}
