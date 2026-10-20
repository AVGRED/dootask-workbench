const base = "http://127.0.0.1:9020";

async function api(path, method = "GET", body, token) {
  const response = await fetch(base + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const json = await response.json();
  return json;
}

const login = (await api("/api/v1/auth/login", "POST", {
  username: "demo@workbench.local",
  password: "secret123"
})).data;
const token = login.token;
const userId = login.userid;

const all = (await api("/api/v1/projects?page=1&size=100", "GET", undefined, token)).data;
let cleared = 0;
for (const project of all.list) {
  const res = await api(`/api/v1/projects/${project.id}`, "DELETE", undefined, token);
  if (res.code === 0) cleared++;
}
console.log(`cleared_projects=${cleared}`);

const project = (await api("/api/v1/projects", "POST", {
  name: "产品迭代 · 工作台演示",
  desc: "首页板块、看板、甘特、报表的演示数据"
}, token)).data;
console.log(`project=${project.id}`);

const tasks = [
  { name: "梳理需求与验收标准", startAt: "2026-10-20T09:00:00", endAt: "2026-10-22T18:00:00", pLevel: 1, ownerUserid: userId },
  { name: "设计首页板块布局", startAt: "2026-10-22T09:00:00", endAt: "2026-10-27T18:00:00", pLevel: 1, ownerUserid: userId },
  { name: "实现看板拖拽", startAt: "2026-10-26T09:00:00", endAt: "2026-10-30T18:00:00", pLevel: 2, ownerUserid: userId },
  { name: "甘特视图联调", startAt: "2026-10-29T09:00:00", endAt: "2026-11-03T18:00:00", pLevel: 2, ownerUserid: userId },
  { name: "回归测试与上线", startAt: "2026-11-03T09:00:00", endAt: "2026-11-06T18:00:00", pLevel: 3, ownerUserid: userId }
];
const ids = [];
for (const task of tasks) {
  ids.push((await api(`/api/v1/projects/${project.id}/tasks`, "POST", task, token)).data.id);
}
console.log(`tasks=${ids.join(",")}`);

await api(`/api/v1/projects/${project.id}/dependencies`, "POST", { predecessorTaskId: ids[0], successorTaskId: ids[1] }, token);
await api(`/api/v1/projects/${project.id}/dependencies`, "POST", { predecessorTaskId: ids[1], successorTaskId: ids[2] }, token);

const tag = (await api(`/api/v1/projects/${project.id}/tags`, "POST", { name: "前端", color: "#409eff" }, token)).data;
await api(`/api/v1/tasks/${ids[1]}/tags`, "POST", { tagId: tag.id }, token);

await api(`/api/v1/tasks/${ids[0]}/complete`, "POST", {}, token);
await api(`/api/v1/tasks/${ids[0]}/worklogs`, "POST", { requestId: "demo-wl-1", minutes: 240, workDate: "2026-10-20" }, token);
await api(`/api/v1/tasks/${ids[1]}/worklogs`, "POST", { requestId: "demo-wl-2", minutes: 180, workDate: "2026-10-20" }, token);
await api("/api/v1/me/status", "PUT", { status: "focus", note: "推进工作台改版" }, token);

const report = await api("/api/v1/daily-reports?date=2026-10-20", "POST", {
  contentDone: "完成首页可拖拽板块与项目卡片",
  contentBlocker: "管理端报表还需要补导出",
  contentPlan: "优化移动端看板",
  minutes: 420
}, token);
console.log(`report_code=${report.code}`);

const final = (await api("/api/v1/projects?page=1&size=100", "GET", undefined, token)).data;
console.log(`projects_after=${final.total}`);
