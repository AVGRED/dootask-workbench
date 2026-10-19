package com.workbench.planning.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workbench.planning.schedule.ScheduleCalculator.Change;
import com.workbench.planning.schedule.ScheduleCalculator.Edge;
import com.workbench.planning.schedule.ScheduleCalculator.TaskSlot;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 排期算法测试：工作日 09:00-18:00，时长按原任务保留。
 * 2026-10-05 是周一，10-09 周五，10-10 周六，10-12 周一。
 */
class ScheduleCalculatorTest {

    private static final Set<DayOfWeek> WEEKDAYS = Set.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static LocalDateTime at(int day, int hour) {
        return LocalDateTime.of(2026, 10, day, hour, 0);
    }

    private static LocalDateTime workday(int day) {
        return LocalDateTime.of(2026, 10, day, 9, 0);
    }

    @Test
    void shiftsSuccessorToNextWorkSlot() {
        List<TaskSlot> tasks = List.of(
                new TaskSlot(1, at(5, 9), at(5, 18)),
                new TaskSlot(2, at(6, 9), at(6, 18)));
        List<Edge> edges = List.of(new Edge(1, 2));

        List<Change> changes = ScheduleCalculator.preview(tasks, edges, 1, at(5, 9), at(7, 18), WEEKDAYS);

        assertEquals(2, changes.size());
        Change successor = changes.stream().filter(change -> change.taskId() == 2).findFirst().orElseThrow();
        // 锚点改到 10-07（周三）18:00 结束，后置任务顺延到 10-08（周四）09:00，时长 9 小时保持不变
        assertEquals(at(8, 9), successor.newStart());
        assertEquals(at(8, 18), successor.newEnd());
    }

    @Test
    void keepsSuccessorWhenItAlreadyStartsLater() {
        List<TaskSlot> tasks = List.of(
                new TaskSlot(1, at(5, 9), at(5, 18)),
                new TaskSlot(2, at(9, 9), at(9, 18)));
        List<Edge> edges = List.of(new Edge(1, 2));

        List<Change> changes = ScheduleCalculator.preview(tasks, edges, 1, at(5, 9), at(6, 18), WEEKDAYS);

        assertEquals(1, changes.size());
        assertEquals(1, changes.get(0).taskId());
    }

    @Test
    void propagatesThroughChainAndSkipsWeekend() {
        List<TaskSlot> tasks = List.of(
                new TaskSlot(1, at(5, 9), at(5, 18)),
                new TaskSlot(2, at(6, 9), at(6, 18)),
                new TaskSlot(3, at(7, 9), at(7, 18)));
        List<Edge> edges = List.of(new Edge(1, 2), new Edge(2, 3));

        // 锚点推迟到 10-08（周四）18:00 结束：任务2 -> 10-09（周五）09:00，任务3 -> 10-12（周一）09:00
        List<Change> changes = ScheduleCalculator.preview(tasks, edges, 1, at(5, 9), at(8, 18), WEEKDAYS);

        assertEquals(3, changes.size());
        Change second = changes.stream().filter(change -> change.taskId() == 2).findFirst().orElseThrow();
        Change third = changes.stream().filter(change -> change.taskId() == 3).findFirst().orElseThrow();
        assertEquals(at(9, 9), second.newStart());
        assertEquals(at(12, 9), third.newStart());
        assertEquals(DayOfWeek.MONDAY, third.newStart().getDayOfWeek());
    }

    @Test
    void ignoresAnchorOutsideProject() {
        List<TaskSlot> tasks = List.of(new TaskSlot(1, at(5, 9), at(5, 18)));

        List<Change> changes = ScheduleCalculator.preview(tasks, List.of(), 99, at(5, 9), at(6, 18), WEEKDAYS);

        assertTrue(changes.isEmpty());
    }

    @Test
    void doesNotLoopOnCyclicEdges() {
        List<TaskSlot> tasks = List.of(
                new TaskSlot(1, at(5, 9), at(5, 18)),
                new TaskSlot(2, at(6, 9), at(6, 18)));
        List<Edge> edges = List.of(new Edge(1, 2), new Edge(2, 1));

        List<Change> changes = ScheduleCalculator.preview(tasks, edges, 1, at(5, 9), at(7, 18), WEEKDAYS);

        assertTrue(changes.size() <= 2);
    }

    @Test
    void customWorkdaysAreRespected() {
        List<TaskSlot> tasks = List.of(
                new TaskSlot(1, at(5, 9), at(5, 18)),
                new TaskSlot(2, at(6, 9), at(6, 18)));
        List<Edge> edges = List.of(new Edge(1, 2));

        // 只把周一、周三、周五当工作日：10-08（周四）应顺延到 10-09（周五）
        List<Change> changes = ScheduleCalculator.preview(tasks, edges, 1, at(5, 9), at(7, 18),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        Change successor = changes.stream().filter(change -> change.taskId() == 2).findFirst().orElseThrow();
        assertEquals(DayOfWeek.FRIDAY, successor.newStart().getDayOfWeek());
        assertEquals(workday(9), successor.newStart());
    }
}
