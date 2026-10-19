package com.workbench.planning.schedule;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动排期预演的核心算法（纯函数，不访问数据库）。
 *
 * <p>规则：前置任务完成后后置任务才能开始（FS），因此后置任务最早从前置结束后的
 * <b>下一个工作时段</b>开始（默认 09:00）；时长（分钟）保持不变；周五之后顺延到周一。</p>
 */
public final class ScheduleCalculator {

    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);

    private ScheduleCalculator() {
    }

    public record TaskSlot(long id, LocalDateTime start, LocalDateTime end) {
    }

    public record Edge(long predecessorId, long successorId) {
    }

    public record Change(long taskId, LocalDateTime oldStart, LocalDateTime oldEnd,
                         LocalDateTime newStart, LocalDateTime newEnd) {
    }

    public static List<Change> preview(
            List<TaskSlot> tasks,
            List<Edge> edges,
            long anchorId,
            LocalDateTime anchorStart,
            LocalDateTime anchorEnd,
            Set<DayOfWeek> workdays) {
        Map<Long, TaskSlot> slots = new HashMap<>();
        for (TaskSlot slot : tasks) {
            slots.put(slot.id(), slot);
        }
        if (!slots.containsKey(anchorId)) {
            return List.of();
        }

        Map<Long, List<Long>> successors = new HashMap<>();
        for (Edge edge : edges) {
            successors.computeIfAbsent(edge.predecessorId(), key -> new ArrayList<>()).add(edge.successorId());
        }

        Map<Long, TaskSlot> planned = new HashMap<>();
        planned.put(anchorId, new TaskSlot(anchorId, anchorStart, anchorEnd));

        List<Change> changes = new ArrayList<>();
        if (!anchorStart.equals(slots.get(anchorId).start()) || !anchorEnd.equals(slots.get(anchorId).end())) {
            changes.add(new Change(anchorId, slots.get(anchorId).start(), slots.get(anchorId).end(),
                    anchorStart, anchorEnd));
        }

        Deque<Long> queue = new ArrayDeque<>();
        queue.add(anchorId);
        Set<Long> visited = new HashSet<>();
        visited.add(anchorId);

        while (!queue.isEmpty()) {
            long current = queue.poll();
            TaskSlot predecessor = planned.get(current);
            if (predecessor == null || predecessor.end() == null) {
                continue;
            }
            for (Long nextId : successors.getOrDefault(current, List.of())) {
                if (!visited.add(nextId)) {
                    continue;
                }
                TaskSlot original = slots.get(nextId);
                if (original == null) {
                    continue;
                }
                LocalDateTime start = original.start();
                LocalDateTime end = original.end();
                if (start != null && start.isBefore(predecessor.end())) {
                    long durationMinutes = end == null ? 0 : Duration.between(start, end).toMinutes();
                    LocalDateTime shiftedStart = nextWorkSlotStart(predecessor.end(), workdays);
                    LocalDateTime shiftedEnd = durationMinutes > 0 ? shiftedStart.plusMinutes(durationMinutes) : null;
                    changes.add(new Change(nextId, start, end, shiftedStart, shiftedEnd));
                    planned.put(nextId, new TaskSlot(nextId, shiftedStart, shiftedEnd));
                } else {
                    planned.put(nextId, original);
                }
                queue.add(nextId);
            }
        }
        return changes;
    }

    /**
     * 前置结束时刻之后的下一个工作时段起点：
     * 当天是工作日且结束时刻早于 09:00 时，可以当天 09:00 开始；否则一律顺延到下一个工作日的 09:00。
     */
    static LocalDateTime nextWorkSlotStart(LocalDateTime moment, Set<DayOfWeek> workdays) {
        Set<DayOfWeek> effective = effectiveWorkdays(workdays);
        LocalDate date = moment.toLocalDate();
        LocalTime time = moment.toLocalTime();
        if (!effective.contains(date.getDayOfWeek()) || !time.isBefore(WORK_START)) {
            date = date.plusDays(1);
        }
        for (int guard = 0; guard < 14; guard++) {
            if (effective.contains(date.getDayOfWeek())) {
                return LocalDateTime.of(date, WORK_START);
            }
            date = date.plusDays(1);
        }
        return LocalDateTime.of(date, WORK_START);
    }

    static Set<DayOfWeek> effectiveWorkdays(Set<DayOfWeek> workdays) {
        if (workdays == null || workdays.isEmpty()) {
            return Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        }
        return workdays;
    }

    public static boolean isWorkday(LocalDate date, Set<DayOfWeek> workdays) {
        return effectiveWorkdays(workdays).contains(date.getDayOfWeek());
    }
}
