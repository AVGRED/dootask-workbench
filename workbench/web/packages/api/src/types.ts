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
}

export interface SaveTaskRequest {
  name: string;
  desc?: string;
  ownerUserid?: number | null;
  startAt?: string | null;
  endAt?: string | null;
  pLevel?: number | null;
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
