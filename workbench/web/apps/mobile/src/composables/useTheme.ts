import { ref } from "vue";

const STORAGE_KEY = "workbench.theme";
const theme = ref<"light" | "dark">("light");

function apply(value: "light" | "dark"): void {
  theme.value = value;
  const root = document.documentElement;
  root.classList.toggle("dark", value === "dark");
  localStorage.setItem(STORAGE_KEY, value);
}

/** 主题切换：默认跟随上次选择，首次进入跟随系统。 */
export function useTheme() {
  function initTheme(): void {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === "dark" || saved === "light") {
      apply(saved);
      return;
    }
    const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false;
    apply(prefersDark ? "dark" : "light");
  }

  function toggleTheme(): void {
    apply(theme.value === "dark" ? "light" : "dark");
  }

  return { theme, initTheme, toggleTheme };
}
