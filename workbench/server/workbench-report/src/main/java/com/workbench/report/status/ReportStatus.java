package com.workbench.report.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日报状态判定（纯函数）：
 * <ul>
 *   <li>1 = 准时：当天或次日 10:00 前提交；</li>
 *   <li>2 = 迟交：超过次日 10:00 但仍在 7 天补交窗口内；</li>
 *   <li>3 = 补交：报告日期早于「今天 - 7 天」的补交；</li>
 * </ul>
 * 超过补交窗口的提交由服务层拒绝。
 */
public final class ReportStatus {

    public static final int ON_TIME = 1;
    public static final int LATE = 2;
    public static final int BACKFILL = 3;

    /** 补交窗口（天）。 */
    public static final int BACKFILL_DAYS = 7;

    private ReportStatus() {
    }

    public static boolean withinBackfillWindow(LocalDate reportDate, LocalDateTime now) {
        return !reportDate.isBefore(now.toLocalDate().minusDays(BACKFILL_DAYS));
    }

    public static int evaluate(LocalDate reportDate, LocalDateTime now) {
        if (!withinBackfillWindow(reportDate, now)) {
            throw new IllegalArgumentException("超过补交窗口");
        }
        LocalDateTime deadline = reportDate.plusDays(1).atTime(10, 0);
        if (!now.isAfter(deadline)) {
            return ON_TIME;
        }
        // 刚过截止时间（今天/昨天）算迟交；更早的报告属于补交。
        return reportDate.isBefore(now.toLocalDate().minusDays(1)) ? BACKFILL : LATE;
    }

    public static String text(int status) {
        return switch (status) {
            case ON_TIME -> "准时";
            case LATE -> "迟交";
            case BACKFILL -> "补交";
            default -> "未知";
        };
    }

    public static int workdaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }
}
