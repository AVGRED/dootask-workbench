package com.workbench.worklog.capacity;

import com.workbench.worklog.web.WorklogDtos.CapacityMember;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 容量汇总（纯函数）：把工时记录按人聚合，按工作日 × 每日 8 小时折算可用容量。
 *
 * <p>区间上限 93 天由调用方校验；这里只做计算。</p>
 */
public final class CapacityCalculator {

    public static final int DAILY_MINUTES = 8 * 60;

    private static final Set<DayOfWeek> WEEKDAYS = Set.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private CapacityCalculator() {
    }

    public record LogRow(long userid, long taskId, int minutes) {
    }

    public record Result(int workdays, List<CapacityMember> members) {
    }

    public static int workdaysBetween(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            if (WEEKDAYS.contains(cursor.getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }

    public static Result summarize(LocalDate from, LocalDate to, List<LogRow> rows) {
        int workdays = workdaysBetween(from, to);
        long available = (long) workdays * DAILY_MINUTES;
        Map<Long, long[]> byUser = new LinkedHashMap<>();
        Map<Long, Set<Long>> tasksByUser = new LinkedHashMap<>();
        for (LogRow row : rows) {
            long[] agg = byUser.computeIfAbsent(row.userid(), key -> new long[]{0});
            agg[0] += row.minutes();
            tasksByUser.computeIfAbsent(row.userid(), key -> new java.util.LinkedHashSet<>()).add(row.taskId());
        }
        List<CapacityMember> members = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : byUser.entrySet()) {
            long logged = entry.getValue()[0];
            long taskCount = tasksByUser.getOrDefault(entry.getKey(), Set.of()).size();
            long utilization = available == 0 ? 0 : Math.round(logged * 100.0 / available);
            long overrun = Math.max(0, logged - available);
            members.add(new CapacityMember(entry.getKey(), logged, available, utilization, overrun, taskCount));
        }
        members.sort(Comparator.comparingLong(CapacityMember::userid));
        return new Result(workdays, members);
    }
}
