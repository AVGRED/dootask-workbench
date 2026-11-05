/** 会议（目前是「粘贴腾讯会议链接 + 通知参会人」）。 */
export interface MeetingView {
  id: number;
  title: string;
  project_id: number | null;
  project_name?: string;
  owner_userid: number;
  owner_name?: string;
  start_at: string;
  duration_minutes: number;
  join_url: string;
  meeting_code?: string;
  platform?: string;
  description?: string;
  created_at?: string;
}

export interface SaveMeetingRequest {
  title: string;
  projectId?: number | null;
  startAt: string;
  durationMinutes?: number | null;
  joinUrl: string;
  meetingCode?: string | null;
  description?: string | null;
}
