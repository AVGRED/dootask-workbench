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
