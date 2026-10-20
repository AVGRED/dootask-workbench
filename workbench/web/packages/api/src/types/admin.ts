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

export interface AdminProjectRow {
  id: number;
  name: string;
  owner_userid: number;
  owner_name: string | null;
  members: number;
  total: number;
  done: number;
  overdue: number;
  updated_at: string | null;
}

export interface AdminReportRow {
  id: number;
  userid: number;
  nickname: string | null;
  email: string | null;
  report_date: string;
  content_done: string;
  content_blocker: string | null;
  content_plan: string | null;
  minutes: number | null;
  status: number;
  submitted_at: string | null;
}

export interface TeamStatusRow {
  userid: number;
  nickname: string;
  email: string;
  avatar: string | null;
  status: string;
  note: string;
  status_at: string | null;
  current_task: string | null;
  report_today: number;
  week_minutes: number;
}

/** 星图节点：一个员工就是一颗星。 */
export interface StarNode {
  userid: number;
  nickname: string;
  email: string;
  role: string;
  avatar: string | null;
  status: string;
  note: string;
  projectCount: number;
  self: boolean;
}

/** 星图连线：共同参与过项目的人数权重。 */
export interface StarEdge {
  from: number;
  to: number;
  weight: number;
}

export interface StarMapView {
  scope: "all" | "collaborators";
  centerUserid: number;
  nodes: StarNode[];
  edges: StarEdge[];
}
