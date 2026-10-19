import type {
  ApiResponse,
  CreateUserRequest,
  DepartmentView,
  LoginRequest,
  LoginResponse,
  MemberView,
  PageResult,
  ProjectView,
  SaveProjectRequest,
  UserView,
} from "./types";

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
    return this.request<LoginResponse>("POST", "/api/v1/auth/login", payload);
  }

  async listUsers(params: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<UserView>> {
    const query = new URLSearchParams();
    query.set("page", String(params.page ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.keyword) query.set("keyword", params.keyword);
    return this.request<PageResult<UserView>>("GET", `/api/v1/users?${query.toString()}`);
  }

  async createUser(payload: CreateUserRequest): Promise<UserView> {
    return this.request<UserView>("POST", "/api/v1/users", payload);
  }

  async deleteUser(userid: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/users/${userid}`);
  }

  async listDepartments(): Promise<DepartmentView[]> {
    return this.request<DepartmentView[]>("GET", "/api/v1/departments");
  }

  async listProjects(params: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<ProjectView>> {
    const query = new URLSearchParams();
    query.set("page", String(params.page ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.keyword) query.set("keyword", params.keyword);
    return this.request<PageResult<ProjectView>>("GET", `/api/v1/projects?${query.toString()}`);
  }

  async createProject(payload: SaveProjectRequest): Promise<ProjectView> {
    return this.request<ProjectView>("POST", "/api/v1/projects", payload);
  }

  async archiveProject(projectId: number): Promise<void> {
    await this.request<null>("POST", `/api/v1/projects/${projectId}/archive`);
  }

  async deleteProject(projectId: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/projects/${projectId}`);
  }

  async listMembers(projectId: number): Promise<MemberView[]> {
    return this.request<MemberView[]>("GET", `/api/v1/projects/${projectId}/members`);
  }

  async addMember(projectId: number, userid: number): Promise<MemberView> {
    return this.request<MemberView>("POST", `/api/v1/projects/${projectId}/members`, { userid });
  }

  async removeMember(projectId: number, userid: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/projects/${projectId}/members/${userid}`);
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    const token = this.options.getToken?.();
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${this.options.baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const payload = (await response.json()) as ApiResponse<T>;
    if (payload.code !== 0) throw new ApiError(payload.code, payload.message);
    return payload.data;
  }
}
