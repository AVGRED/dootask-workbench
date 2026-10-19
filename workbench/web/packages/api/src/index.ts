import type {
  ActivityView,
  ApiResponse,
  ApplyScheduleRequest,
  ApplyTemplateResult,
  AttachmentView,
  CapacitySummary,
  BoardView,
  BoardColumn,
  CommentView,
  CreateCommentRequest,
  CreateUserRequest,
  CreateWorklogRequest,
  DailyReportView,
  DepartmentView,
  DependencyView,
  DictItemView,
  GanttView,
  LoginRequest,
  LoginResponse,
  MemberView,
  MilestoneView,
  NotificationView,
  PageResult,
  ProjectView,
  PublicSettingsView,
  RiskSummaryView,
  SaveDailyReportRequest,
  SaveDictRequest,
  SearchResult,
  SaveMilestoneRequest,
  SaveProjectRequest,
  SaveTaskRequest,
  SaveTemplateRequest,
  ScheduleBatchView,
  SchedulePreviewRequest,
  SchedulePreviewResponse,
  SettingView,
  TaskView,
  TemplateRevisionView,
  TemplateView,
  UnreadCountView,
  UserView,
  WeeklySummaryView,
  WorklogView,
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

  async listTasks(
    projectId: number,
    params: { page?: number; size?: number; keyword?: string; ownerUserid?: number; milestoneId?: number } = {}
  ): Promise<PageResult<TaskView>> {
    const query = new URLSearchParams();
    query.set("page", String(params.page ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.keyword) query.set("keyword", params.keyword);
    if (params.ownerUserid) query.set("ownerUserid", String(params.ownerUserid));
    if (params.milestoneId) query.set("milestoneId", String(params.milestoneId));
    return this.request<PageResult<TaskView>>("GET", `/api/v1/projects/${projectId}/tasks?${query.toString()}`);
  }

  async createTask(projectId: number, payload: SaveTaskRequest): Promise<TaskView> {
    return this.request<TaskView>("POST", `/api/v1/projects/${projectId}/tasks`, payload);
  }

  async updateTask(taskId: number, payload: SaveTaskRequest): Promise<TaskView> {
    return this.request<TaskView>("PUT", `/api/v1/tasks/${taskId}`, payload);
  }

  async completeTask(taskId: number): Promise<void> {
    await this.request<null>("POST", `/api/v1/tasks/${taskId}/complete`);
  }

  async reopenTask(taskId: number): Promise<void> {
    await this.request<null>("POST", `/api/v1/tasks/${taskId}/reopen`);
  }

  async deleteTask(taskId: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/tasks/${taskId}`);
  }

  async listMilestones(projectId: number): Promise<MilestoneView[]> {
    return this.request<MilestoneView[]>("GET", `/api/v1/projects/${projectId}/milestones`);
  }

  async createMilestone(projectId: number, payload: SaveMilestoneRequest): Promise<MilestoneView> {
    return this.request<MilestoneView>("POST", `/api/v1/projects/${projectId}/milestones`, payload);
  }

  async deleteMilestone(projectId: number, milestoneId: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/projects/${projectId}/milestones/${milestoneId}`);
  }

  async listDependencies(projectId: number): Promise<DependencyView[]> {
    return this.request<DependencyView[]>("GET", `/api/v1/projects/${projectId}/dependencies`);
  }

  async addDependency(
    projectId: number,
    predecessorTaskId: number,
    successorTaskId: number
  ): Promise<DependencyView> {
    return this.request<DependencyView>("POST", `/api/v1/projects/${projectId}/dependencies`, {
      predecessorTaskId,
      successorTaskId
    });
  }

  async dependencyRisk(projectId: number): Promise<RiskSummaryView> {
    return this.request<RiskSummaryView>("GET", `/api/v1/projects/${projectId}/dependencies/risk-summary`);
  }

  async previewSchedule(projectId: number, payload: SchedulePreviewRequest): Promise<SchedulePreviewResponse> {
    return this.request<SchedulePreviewResponse>("POST", `/api/v1/projects/${projectId}/schedule/preview`, payload);
  }

  async listWorklogs(taskId: number): Promise<WorklogView[]> {
    return this.request<WorklogView[]>("GET", `/api/v1/tasks/${taskId}/worklogs`);
  }

  async createWorklog(taskId: number, payload: CreateWorklogRequest): Promise<WorklogView> {
    return this.request<WorklogView>("POST", `/api/v1/tasks/${taskId}/worklogs`, payload);
  }

  async deleteWorklog(id: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/worklogs/${id}`);
  }

  async projectCapacity(projectId: number, from: string, to: string): Promise<CapacitySummary> {
    const query = new URLSearchParams({ from, to });
    return this.request<CapacitySummary>("GET", `/api/v1/projects/${projectId}/capacity?${query.toString()}`);
  }

  async listDailyReports(from?: string, to?: string): Promise<DailyReportView[]> {
    const query = new URLSearchParams();
    if (from) query.set("from", from);
    if (to) query.set("to", to);
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return this.request<DailyReportView[]>("GET", `/api/v1/daily-reports${suffix}`);
  }

  async dailyReportByDate(date: string): Promise<DailyReportView | null> {
    return this.request<DailyReportView | null>("GET", `/api/v1/daily-reports/${date}`);
  }

  async saveDailyReport(date: string, payload: SaveDailyReportRequest): Promise<DailyReportView> {
    return this.request<DailyReportView>("POST", `/api/v1/daily-reports?date=${date}`, payload);
  }

  async deleteDailyReport(id: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/daily-reports/${id}`);
  }

  async listWeeklySummaries(): Promise<WeeklySummaryView[]> {
    return this.request<WeeklySummaryView[]>("GET", "/api/v1/weekly-summaries");
  }

  async regenerateWeeklySummary(weekStart?: string): Promise<WeeklySummaryView> {
    return this.request<WeeklySummaryView>("POST", "/api/v1/weekly-summaries/regenerate", { weekStart });
  }

  async listComments(taskId: number): Promise<CommentView[]> {
    return this.request<CommentView[]>("GET", `/api/v1/tasks/${taskId}/comments`);
  }

  async createComment(taskId: number, payload: CreateCommentRequest): Promise<CommentView> {
    return this.request<CommentView>("POST", `/api/v1/tasks/${taskId}/comments`, payload);
  }

  async deleteComment(id: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/comments/${id}`);
  }

  async listNotifications(unreadOnly = false): Promise<NotificationView[]> {
    return this.request<NotificationView[]>("GET", `/api/v1/notifications?unreadOnly=${unreadOnly}`);
  }

  async unreadCount(): Promise<number> {
    const result = await this.request<UnreadCountView>("GET", "/api/v1/notifications/unread-count");
    return result.unread;
  }

  async markNotificationRead(id: number): Promise<void> {
    await this.request<null>("POST", `/api/v1/notifications/${id}/read`);
  }

  async markAllNotificationsRead(): Promise<number> {
    const result = await this.request<UnreadCountView>("POST", "/api/v1/notifications/read-all");
    return result.unread;
  }

  async listAttachments(taskId: number): Promise<AttachmentView[]> {
    return this.request<AttachmentView[]>("GET", `/api/v1/tasks/${taskId}/attachments`);
  }

  /** multipart 上传：不能带 Content-Type，交给浏览器设置 boundary。 */
  async uploadAttachment(taskId: number, file: File): Promise<AttachmentView> {
    const form = new FormData();
    form.append("file", file);
    const headers: Record<string, string> = {};
    const token = this.options.getToken?.();
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${this.options.baseUrl}/api/v1/tasks/${taskId}/attachments`, {
      method: "POST",
      headers,
      body: form
    });
    const payload = await parsePayload<AttachmentView>(response);
    if (payload.code !== 0) throw new ApiError(payload.code, payload.message);
    return payload.data;
  }

  async deleteAttachment(id: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/attachments/${id}`);
  }

  /** 下载走鉴权请求再转 Blob：直接把 URL 放进 <a href> 不会带 Authorization 头。 */
  async downloadAttachment(id: number): Promise<Blob> {
    const headers: Record<string, string> = {};
    const token = this.options.getToken?.();
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${this.options.baseUrl}/api/v1/attachments/${id}/download`, { headers });
    if (!response.ok) throw new ApiError(response.status, `下载失败（${response.status}）`);
    return response.blob();
  }

  async listTemplates(projectId: number): Promise<TemplateView[]> {
    return this.request<TemplateView[]>("GET", `/api/v1/projects/${projectId}/templates`);
  }

  async createTemplate(projectId: number, payload: SaveTemplateRequest): Promise<TemplateView> {
    return this.request<TemplateView>("POST", `/api/v1/projects/${projectId}/templates`, payload);
  }

  async updateTemplate(id: number, payload: SaveTemplateRequest): Promise<TemplateView> {
    return this.request<TemplateView>("PUT", `/api/v1/templates/${id}`, payload);
  }

  async templateRevisions(id: number): Promise<TemplateRevisionView[]> {
    return this.request<TemplateRevisionView[]>("GET", `/api/v1/templates/${id}/revisions`);
  }

  async applyTemplate(id: number): Promise<ApplyTemplateResult> {
    return this.request<ApplyTemplateResult>("POST", `/api/v1/templates/${id}/apply`);
  }

  async deleteTemplate(id: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/templates/${id}`);
  }

  async applySchedule(projectId: number, payload: ApplyScheduleRequest): Promise<ScheduleBatchView> {
    return this.request<ScheduleBatchView>("POST", `/api/v1/projects/${projectId}/schedule/apply`, payload);
  }

  async undoSchedule(batchId: number): Promise<ScheduleBatchView> {
    return this.request<ScheduleBatchView>("POST", `/api/v1/schedule/batches/${batchId}/undo`);
  }

  async listScheduleBatches(projectId: number): Promise<ScheduleBatchView[]> {
    return this.request<ScheduleBatchView[]>("GET", `/api/v1/projects/${projectId}/schedule/batches`);
  }

  async listActivities(
    projectId: number,
    params: { page?: number; size?: number; taskId?: number } = {}
  ): Promise<PageResult<ActivityView>> {
    const query = new URLSearchParams();
    query.set("page", String(params.page ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.taskId) query.set("taskId", String(params.taskId));
    return this.request<PageResult<ActivityView>>(
      "GET",
      `/api/v1/projects/${projectId}/activities?${query.toString()}`
    );
  }

  async listSettings(): Promise<SettingView[]> {
    return this.request<SettingView[]>("GET", "/api/v1/admin/settings");
  }

  async saveSetting(key: string, value: string, description?: string): Promise<SettingView> {
    return this.request<SettingView>("PUT", `/api/v1/admin/settings/${encodeURIComponent(key)}`, {
      value,
      description
    });
  }

  async publicSettings(): Promise<PublicSettingsView> {
    return this.request<PublicSettingsView>("GET", "/api/v1/system/public/settings");
  }

  async listDicts(type: string): Promise<DictItemView[]> {
    return this.request<DictItemView[]>("GET", `/api/v1/dicts/${encodeURIComponent(type)}`);
  }

  async adminDicts(type?: string): Promise<DictItemView[]> {
    const suffix = type ? `?type=${encodeURIComponent(type)}` : "";
    return this.request<DictItemView[]>("GET", `/api/v1/admin/dicts${suffix}`);
  }

  async createDict(payload: SaveDictRequest): Promise<DictItemView> {
    return this.request<DictItemView>("POST", "/api/v1/admin/dicts", payload);
  }

  async deleteDict(id: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/admin/dicts/${id}`);
  }

  async changeRole(userid: number, role: string): Promise<UserView> {
    return this.request<UserView>("PUT", `/api/v1/users/${userid}/role`, { role });
  }

  async search(keyword: string): Promise<SearchResult> {
    return this.request<SearchResult>("GET", `/api/v1/search?keyword=${encodeURIComponent(keyword)}`);
  }

  async board(projectId: number): Promise<BoardView> {
    return this.request<BoardView>("GET", `/api/v1/projects/${projectId}/board`);
  }

  async createColumn(projectId: number, name: string): Promise<BoardColumn> {
    return this.request<BoardColumn>("POST", `/api/v1/projects/${projectId}/columns`, { name });
  }

  async deleteColumn(projectId: number, columnId: number): Promise<void> {
    await this.request<null>("DELETE", `/api/v1/projects/${projectId}/columns/${columnId}`);
  }

  async moveTask(taskId: number, columnId: number | null): Promise<void> {
    await this.request<null>("PUT", `/api/v1/tasks/${taskId}/move`, { columnId });
  }

  async gantt(projectId: number): Promise<GanttView> {
    return this.request<GanttView>("GET", `/api/v1/projects/${projectId}/gantt`);
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
    const payload = await parsePayload<T>(response);
    if (payload.code !== 0) throw new ApiError(payload.code, payload.message);
    return payload.data;
  }
}

/**
 * 解析响应体：dev server 代理不可达、或后端返回非 JSON 时，
 * 直接抛带状态码的可读错误，而不是让 `response.json()` 抛出
 * "Unexpected end of JSON input" 这类对用户无意义的异常。
 */
async function parsePayload<T>(response: Response): Promise<ApiResponse<T>> {
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
