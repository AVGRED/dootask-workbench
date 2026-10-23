import { defineStore } from "pinia";
import { ApiClient, type LoginRequest, type LoginResponse } from "@workbench/api";

const TOKEN_KEY = "workbench.admin.token";
const USER_KEY = "workbench.admin.user";

const client = new ApiClient({
  baseUrl: "",
  getToken: () => localStorage.getItem(TOKEN_KEY)
});

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? "",
    user: JSON.parse(localStorage.getItem(USER_KEY) ?? "null") as LoginResponse | null
  }),
  getters: {
    isLoggedIn: (state) => state.token.length > 0
  },
  actions: {
    async login(payload: LoginRequest): Promise<void> {
      const data = await client.login(payload);
      this.token = data.token;
      this.user = data;
      localStorage.setItem(TOKEN_KEY, data.token);
      localStorage.setItem(USER_KEY, JSON.stringify(data));
      // 员工端与管理端同源：登录一次，两边都能直接用
      localStorage.setItem("workbench.token", data.token);
      localStorage.setItem("workbench.user", JSON.stringify(data));
    },
    logout(): void {
      this.token = "";
      this.user = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem("workbench.token");
      localStorage.removeItem("workbench.user");
    }
  }
});

export { client };
