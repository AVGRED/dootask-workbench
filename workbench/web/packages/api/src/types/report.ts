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
