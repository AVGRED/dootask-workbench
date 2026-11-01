/**
 * 统一的业务时区（北京时间）。
 *
 * 前端以前用 `new Date().toISOString().slice(0, 10)` 取"今天"，
 * 那是 UTC 日期：北京时间 0:00–8:00 之间会拿到前一天，导致日报、
 * 任务日期默认值错一天。这里统一按 Asia/Shanghai 计算。
 */
export const BUSINESS_TIME_ZONE = "Asia/Shanghai";

const dateFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: BUSINESS_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit"
});

/** 把任意 Date 格式化成北京时间下的 YYYY-MM-DD。 */
export function dateInBusinessZone(date: Date = new Date()): string {
  return dateFormatter.format(date);
}

/** 北京时间下的"今天"，YYYY-MM-DD。 */
export function todayInBusinessZone(): string {
  return dateInBusinessZone(new Date());
}
