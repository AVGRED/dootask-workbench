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
  account?: string;
  email: string;
  nickname: string;
  profession: string;
  userimg: string;
  loginNum: number;
  role?: string;
  superAdmin?: number;
}

export interface CreateUserRequest {
  account?: string;
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
