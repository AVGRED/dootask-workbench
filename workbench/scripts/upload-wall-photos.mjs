import fs from "node:fs";
import path from "node:path";

const BASE = "https://yanyunxiaoguang.cn";
const DIR = "C:/Users/H/Desktop/新建文件夹 (2)";

const login = await (await fetch(`${BASE}/api/v1/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username: "401001", password: "HXG20061121." })
})).json();
const token = login.data.token;
const headers = { Authorization: `Bearer ${token}` };

const users = await (await fetch(`${BASE}/api/v1/users?page=1&size=100`, { headers })).json();
const byNickname = new Map(users.data.list.map((u) => [u.nickname, u.userid]));

const files = fs.readdirSync(DIR).filter((name) => /\.(jpg|jpeg|png|webp)$/i.test(name));
for (const file of files) {
  const nickname = path.parse(file).name;
  const userid = byNickname.get(nickname);
  if (!userid) {
    console.log(`skip ${file}: 没有匹配的用户`);
    continue;
  }
  const bytes = fs.readFileSync(path.join(DIR, file));
  const form = new FormData();
  const type = file.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
  form.append("file", new Blob([bytes], { type }), file);

  const response = await fetch(`${BASE}/api/v1/admin/users/${userid}/wall-photo`, {
    method: "POST",
    headers, // 不设 Content-Type，让 fetch 自带 boundary
    body: form
  });
  const result = await response.json();
  console.log(`${nickname} (#${userid}) ->`, result.code, result.data?.wallPhoto ?? result.message);
}

const wall = await (await fetch(`${BASE}/api/v1/team/status`, { headers })).json();
for (const row of wall.data) {
  console.log(`${row.userid} ${row.nickname} wall_photo=${row.wall_photo || "(空)"}`);
}
