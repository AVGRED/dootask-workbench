/** 个人待办：不挂在任何项目下，每人自己的事。 */
export interface PersonalTodo {
  id: number;
  title: string;
  done: number;
  todo_date: string | null;
  created_at: string | null;
}

/** 我的资料（自助设置） */
export interface MeProfile {
  userid: number;
  email: string;
  nickname: string;
  profession: string;
  role: string;
  avatar: string;
  superAdmin?: number;
}

/** 管理员发布的通知记录 */
export interface AdminNotice {
  id: number;
  title: string;
  content: string;
  created_by: number;
  created_by_name: string;
  created_at: string | null;
}
