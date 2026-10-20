/** 个人待办：不挂在任何项目下，每人自己的事。 */
export interface PersonalTodo {
  id: number;
  title: string;
  done: number;
  todo_date: string | null;
  created_at: string | null;
}
