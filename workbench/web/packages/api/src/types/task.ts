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

export interface TagView {
  id: number;
  name: string;
  color: string;
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
