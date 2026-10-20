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
