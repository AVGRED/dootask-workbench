/** 会议（管理员创建 + 指定参会人，链接为腾讯会议地址）。 */
export interface MeetingView {
  id: number;
  title: string;
  owner_userid: number;
  owner_name?: string;
  start_at: string;
  duration_minutes: number;
  join_url: string;
  meeting_code?: string;
  platform?: string;
  description?: string;
  created_at?: string;
  participant_userids?: number[];
  participant_names?: string[];
  participant_count?: number;
}

export interface SaveMeetingRequest {
  title: string;
  startAt: string;
  durationMinutes?: number | null;
  joinUrl: string;
  meetingCode?: string | null;
  description?: string | null;
  participantUserids: number[];
}
