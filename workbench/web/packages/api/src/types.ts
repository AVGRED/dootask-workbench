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
