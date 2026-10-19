package com.workbench.worklog.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.workbench.worklog.capacity.CapacityCalculator.LogRow;
import com.workbench.worklog.capacity.CapacityCalculator.Result;
import com.workbench.worklog.web.WorklogDtos.CapacityMember;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 2026-10-05 是周一，10-09 周五，10-12 周一。 */
class CapacityCalculatorTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Test
    void countsWeekdaysOnly() {
        assertEquals(5, CapacityCalculator.workdaysBetween(MONDAY, LocalDate.of(2026, 10, 9)));
        assertEquals(5, CapacityCalculator.workdaysBetween(MONDAY, LocalDate.of(2026, 10, 11)));
        assertEquals(6, CapacityCalculator.workdaysBetween(MONDAY, LocalDate.of(2026, 10, 12)));
        assertEquals(0, CapacityCalculator.workdaysBetween(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11)));
    }

    @Test
    void aggregatesPerUserAndComputesUtilization() {
        List<LogRow> rows = List.of(
                new LogRow(7, 1, 240),
                new LogRow(7, 1, 240),
                new LogRow(7, 2, 60),
                new LogRow(9, 3, 480));

        Result result = CapacityCalculator.summarize(MONDAY, LocalDate.of(2026, 10, 9), rows);

        assertEquals(5, result.workdays());
        assertEquals(2, result.members().size());
        CapacityMember first = result.members().get(0);
        assertEquals(7, first.userid());
        assertEquals(540, first.loggedMinutes());
        assertEquals(2400, first.availableMinutes());
        assertEquals(23, first.utilizationPercent());
        assertEquals(0, first.overrunMinutes());
        assertEquals(2, first.taskCount());
    }

    @Test
    void reportsOverrunWhenLoggedExceedsCapacity() {
        List<LogRow> rows = List.of(new LogRow(7, 1, 3000));

        Result result = CapacityCalculator.summarize(MONDAY, LocalDate.of(2026, 10, 9), rows);

        CapacityMember member = result.members().get(0);
        assertEquals(600, member.overrunMinutes());
        assertEquals(125, member.utilizationPercent());
    }

    @Test
    void emptyRowsProduceNoMembers() {
        Result result = CapacityCalculator.summarize(MONDAY, LocalDate.of(2026, 10, 9), List.of());
        assertEquals(5, result.workdays());
        assertEquals(0, result.members().size());
    }
}
