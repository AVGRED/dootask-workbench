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
