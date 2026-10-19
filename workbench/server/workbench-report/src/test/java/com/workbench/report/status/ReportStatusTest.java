package com.workbench.report.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 2026-10-19 是周一。 */
class ReportStatusTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 15, 0);

    @Test
    void sameDaySubmissionIsOnTime() {
        assertEquals(ReportStatus.ON_TIME, ReportStatus.evaluate(LocalDate.of(2026, 10, 19), NOW));
    }

    @Test
    void beforeNextDayTenIsOnTime() {
        LocalDateTime early = LocalDateTime.of(2026, 10, 20, 9, 30);
        assertEquals(ReportStatus.ON_TIME, ReportStatus.evaluate(LocalDate.of(2026, 10, 19), early));
    }

    @Test
    void afterNextDayTenIsLate() {
        LocalDateTime late = LocalDateTime.of(2026, 10, 20, 11, 0);
        assertEquals(ReportStatus.LATE, ReportStatus.evaluate(LocalDate.of(2026, 10, 19), late));
    }

    @Test
    void olderButWithinSevenDaysIsBackfill() {
        assertEquals(ReportStatus.BACKFILL, ReportStatus.evaluate(LocalDate.of(2026, 10, 16), NOW));
    }

    @Test
    void outsideWindowIsRejected() {
        LocalDate tooOld = LocalDate.of(2026, 10, 11);
        assertFalse(ReportStatus.withinBackfillWindow(tooOld, NOW));
        assertThrows(IllegalArgumentException.class, () -> ReportStatus.evaluate(tooOld, NOW));
    }

    @Test
    void windowBoundaryIsInclusive() {
        LocalDate edge = NOW.toLocalDate().minusDays(ReportStatus.BACKFILL_DAYS);
        assertTrue(ReportStatus.withinBackfillWindow(edge, NOW));
    }

    @Test
    void countsWorkdays() {
        assertEquals(5, ReportStatus.workdaysBetween(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));
        assertEquals(0, ReportStatus.workdaysBetween(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25)));
    }

    @Test
    void statusTextIsLocalized() {
        assertEquals("准时", ReportStatus.text(ReportStatus.ON_TIME));
        assertEquals("迟交", ReportStatus.text(ReportStatus.LATE));
        assertEquals("补交", ReportStatus.text(ReportStatus.BACKFILL));
    }
}
