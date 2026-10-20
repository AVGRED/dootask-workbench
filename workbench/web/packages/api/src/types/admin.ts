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
