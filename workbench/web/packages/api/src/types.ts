export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  userid: number;
  username: string;
  nickname: string;
  role: string;
  expiresIn: number;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export interface UserView {
  userid: number;
  email: string;
  nickname: string;
  profession: string;
  userimg: string;
  loginNum: number;
  role?: string;
}

export interface CreateUserRequest {
  email: string;
  nickname: string;
  profession?: string;
  password: string;
}

export interface DepartmentView {
  id: number;
  name: string;
  parentId: number;
  ownerUserid: number;
  childrenCount: number;
}

export interface ProjectView {
  id: number;
  name: string;
  desc: string;
  ownerUserid: number;
  archived: boolean;
  memberCount: number;
}

export interface SaveProjectRequest {
  name: string;
  desc?: string;
}

export interface MemberView {
  userid: number;
  owner: boolean;
}

export interface TaskView {
  id: number;
  projectId: number;
  name: string;
  desc: string;
  ownerUserid: number;
  startAt: string | null;
  endAt: string | null;
  completeAt: string | null;
  pLevel: number;
  completed: boolean;
  milestoneId?: number | null;
}

export interface SaveTaskRequest {
  name: string;
  desc?: string;
  ownerUserid?: number | null;
  startAt?: string | null;
  endAt?: string | null;
  pLevel?: number | null;
  milestoneId?: number | null;
}

export interface MilestoneView {
  id: number;
  name: string;
  description: string;
  ownerUserid: number;
  targetAt: string | null;
  status: string;
  reminderDays: number;
  sort: number;
  taskCount: number;
  completedCount: number;
}

export interface SaveMilestoneRequest {
  name: string;
  description?: string;
  ownerUserid?: number | null;
  targetAt?: string | null;
  status?: string;
  reminderDays?: number;
}

export interface DependencyView {
  id: number;
  predecessorTaskId: number;
  successorTaskId: number;
}

export interface RiskSummaryView {
  edgeCount: number;
  blockedCount: number;
  unownedCount: number;
  overdueCount: number;
}

export interface SchedulePreviewRequest {
  anchorTaskId: number;
  anchorStart: string;
  anchorEnd: string;
  workdays?: number[];
}

export interface ScheduleChangeView {
  taskId: number;
  oldStart: string | null;
  oldEnd: string | null;
  newStart: string | null;
  newEnd: string | null;
}

export interface SchedulePreviewResponse {
  changes: ScheduleChangeView[];
  changeCount: number;
}

export interface WorklogView {
  id: number;
  taskId: number;
  userid: number;
  minutes: number;
  workDate: string;
  note: string;
}

export interface CreateWorklogRequest {
  requestId: string;
  minutes: number;
  workDate: string;
  note?: string;
}

export interface CapacityMember {
  userid: number;
  loggedMinutes: number;
  availableMinutes: number;
  utilizationPercent: number;
  overrunMinutes: number;
  taskCount: number;
}

export interface CapacitySummary {
  from: string;
  to: string;
  workdays: number;
  dailyMinutes: number;
  members: CapacityMember[];
}

export interface DailyReportView {
  id: number;
  reportDate: string;
  contentDone: string;
  contentBlocker: string;
  contentPlan: string;
  minutes: number | null;
  status: number;
  statusText: string;
}

export interface SaveDailyReportRequest {
  contentDone: string;
  contentBlocker?: string;
  contentPlan?: string;
  minutes?: number | null;
}

export interface WeeklySummaryView {
  id: number;
  weekStart: string;
  weekEnd: string;
  reportCount: number;
  missingCount: number;
  totalMinutes: number;
  content: string;
}

export interface CommentView {
  id: number;
  taskId: number;
  userid: number;
  content: string;
  mentionUserids: number[];
  createdAt: string;
}

export interface CreateCommentRequest {
  content: string;
  mentionUserids?: number[];
}

export interface NotificationView {
  id: number;
  type: string;
  title: string;
  payload: string;
  read: boolean;
  createdAt: string;
}

export interface UnreadCountView {
  unread: number;
}

export interface AttachmentView {
  id: number;
  taskId: number;
  userid: number;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: string;
}

export interface TemplateView {
  id: number;
  name: string;
  content: string;
  milestoneName: string;
  revisionNo: number;
  revisionCount: number;
  updatedAt: string;
}

export interface SaveTemplateRequest {
  name: string;
  content?: string;
  milestoneName?: string;
}

export interface TemplateRevisionView {
  id: number;
  revisionNo: number;
  snapshot: string;
  createdAt: string;
}

export interface ApplyTemplateResult {
  taskId: number;
  taskName: string;
}

export interface ApplyScheduleRequest {
  requestId: string;
  anchorTaskId: number;
  anchorStart: string;
  anchorEnd: string;
  workdays?: number[];
}

export interface ScheduleBatchView {
  id: number;
  requestId: string;
  changeCount: number;
  createdAt: string;
  undone: boolean;
}

export interface ActivityView {
  id: number;
  taskId: number | null;
  userid: number;
  action: string;
  actionText: string;
  detail: string;
  createdAt: string;
}

export interface SettingView {
  key: string;
  value: string;
  description: string;
  updatedAt: string | null;
}

export interface PublicSettingsView {
  items: SettingView[];
}

export interface DictItemView {
  id: number;
  type: string;
  code: string;
  label: string;
  sort: number;
  enabled: boolean;
}

export interface SaveDictRequest {
  type: string;
  code: string;
  label: string;
  sort?: number;
  enabled?: boolean;
}

export interface SearchItem {
  id: number;
  title: string;
  subtitle: string;
  type: string;
}

export interface SearchResult {
  projects: SearchItem[];
  tasks: SearchItem[];
  users: SearchItem[];
}

export interface BoardColumn {
  id: number;
  name: string;
  sort: number;
}

export interface BoardCard {
  id: number;
  name: string;
  desc: string;
  ownerUserid: number;
  pLevel: number;
  completed: boolean;
}

export interface BoardColumnWithCards {
  column: BoardColumn;
  cards: BoardCard[];
}

export interface ReportMemberStat {
  userid: number;
  email: string;
  minutes: number;
  taskCount: number;
}

export interface ReportDayPoint {
  date: string;
  minutes: number;
  completedTasks: number;
}

export interface ProjectReport {
  projectId: number;
  from: string;
  to: string;
  taskTotal: number;
  taskCompleted: number;
  taskOverdue: number;
  completionPercent: number;
  worklogMinutes: number;
  milestoneTotal: number;
  milestoneDone: number;
  members: ReportMemberStat[];
  days: ReportDayPoint[];
}

export interface WidgetDef {
  id: string;
  title: string;
  group: string;
  defaultW: number;
  defaultH: number;
}

export interface LayoutItem {
  id: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DashboardView {
  catalog: WidgetDef[];
  layout: LayoutItem[];
  data: Record<string, unknown>;
  status: string;
  statusNote: string;
}

export interface TeamStatusRow {
  userid: number;
  nickname: string;
  email: string;
  status: string;
  note: string;
  status_at: string | null;
  current_task: string | null;
  report_today: number;
  week_minutes: number;
}

export interface TagView {
  id: number;
  name: string;
  color: string;
}

export interface BoardView {
  projectId: number;
  columns: BoardColumnWithCards[];
}

export interface GanttTask {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  ownerUserid: number;
  pLevel: number;
  completed: boolean;
  milestoneId: number | null;
}

export interface GanttEdge {
  predecessorTaskId: number;
  successorTaskId: number;
}

export interface GanttView {
  projectId: number;
  rangeStart: string;
  rangeEnd: string;
  tasks: GanttTask[];
  edges: GanttEdge[];
}
