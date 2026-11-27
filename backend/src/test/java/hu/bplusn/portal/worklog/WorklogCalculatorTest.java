package hu.bplusn.portal.worklog;

import hu.bplusn.portal.jira.JiraModels.WorklogRow;
import hu.bplusn.portal.worklog.WorklogModels.MonthOverview;
import hu.bplusn.portal.worklog.WorklogModels.MonthStatus;
import hu.bplusn.portal.worklog.WorklogModels.Summary;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorklogCalculatorTest {

    private final WorklogCalculator calculator = new WorklogCalculator(WorkCalendar.weekdaysOnly(), 8);

    private static WorklogRow row(String date, String issue, double hours) {
        String project = issue.substring(0, issue.indexOf('-'));
        return new WorklogRow(date, issue, issue + " összefoglaló", project, Math.round(hours * 3600), "", date + issue);
    }

    @Test
    void weekdayAverageUsesWorkdaysAsDivisor() {
        // 2026-09-01 (kedd) – 2026-09-14 (hétfő): 14 naptári nap, 10 munkanap, 80 óra -> 8 óra/nap
        List<WorklogRow> rows = new ArrayList<>();
        for (LocalDate d = LocalDate.of(2026, 9, 1); !d.isAfter(LocalDate.of(2026, 9, 14)); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() < 6) {
                rows.add(row(d.toString(), "AJI-1", 8));
            }
        }
        Summary summary = calculator.summarize(rows, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 14));
        assertEquals(80.0, summary.totalHours(), 1e-9);
        assertEquals(10, summary.averageWorkingDays());
        assertEquals(8.0, summary.dailyAverage(), 1e-9);
        assertEquals(14, summary.daily().size());
    }

    @Test
    void weekendHoursCountInTotalButNotInDivisor() {
        // 2026-09-07 (hétfő) – 2026-09-13 (vasárnap): 40 hétköznapi + 4 hétvégi óra, 5 munkanap -> 8,8
        List<WorklogRow> rows = new ArrayList<>();
        for (int day = 7; day <= 11; day++) {
            rows.add(row("2026-09-%02d".formatted(day), "AJI-1", 8));
        }
        rows.add(row("2026-09-12", "BNSD-2", 4));
        Summary summary = calculator.summarize(rows, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));
        assertEquals(44.0, summary.totalHours(), 1e-9);
        assertEquals(8.8, summary.dailyAverage(), 1e-9);
        assertEquals(2, summary.projects().size());
        assertEquals("AJI", summary.projects().get(0).key());
    }

    @Test
    void weekendOnlyPeriodHasNoAverage() {
        Summary summary = calculator.summarize(List.of(row("2026-09-12", "AJI-1", 3)),
                LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));
        assertNull(summary.dailyAverage());
        assertEquals(3.0, summary.totalHours(), 1e-9);
    }

    @Test
    void rowsOutsideRangeAreIgnored() {
        List<WorklogRow> rows = List.of(row("2026-08-31", "AJI-1", 1), row("2026-09-01", "AJI-2", 8));
        Summary summary = calculator.summarize(rows, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 17));
        assertEquals(1, summary.worklogCount());
        assertEquals(8.0, summary.totalHours(), 1e-9);
    }

    @Test
    void holidaysAndExtraWorkdaysAreRespected() {
        WorkCalendar calendar = new WorkCalendar(Set.of(LocalDate.of(2026, 10, 23)), Set.of(LocalDate.of(2026, 10, 10)));
        assertFalse(calendar.isWorkday(LocalDate.of(2026, 10, 23))); // péntek, ünnep
        assertTrue(calendar.isWorkday(LocalDate.of(2026, 10, 10)));  // szombat, áthelyezett munkanap
        // 2026 október: 22 hétköznap - 1 ünnep + 1 szombat = 22
        assertEquals(22, calendar.workdays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    void currentMonthUsesElapsedWorkdays() {
        // 2026-09-17 csütörtök: szeptemberben 22 munkanap, 17-ig 13 eltelt
        MonthStatus month = calculator.month(List.of(row("2026-09-01", "AJI-1", 8)), YearMonth.of(2026, 9),
                LocalDate.of(2026, 9, 17));
        assertTrue(month.currentMonth());
        assertEquals(22, month.workingDays());
        assertEquals(13, month.elapsedWorkingDays());
        assertEquals(176.0, month.expectedHours(), 1e-9);
        assertEquals(104.0, month.expectedToDateHours(), 1e-9);
        assertEquals(8.0 - 104.0, month.differenceToDateHours(), 1e-9);
        assertEquals("2026-09-17", month.endDate());
        assertEquals(17, month.daily().size());
    }

    @Test
    void closedMonthUsesWholeMonth() {
        MonthStatus month = calculator.month(List.of(row("2026-08-31", "AJI-1", 1)), YearMonth.of(2026, 8),
                LocalDate.of(2026, 9, 17));
        assertFalse(month.currentMonth());
        assertEquals(21, month.workingDays());
        assertEquals(month.expectedHours(), month.expectedToDateHours(), 1e-9);
        assertEquals("2026-08-31", month.endDate());
        assertEquals("2026. augusztus", month.label());
    }

    @Test
    void leapYearFebruary() {
        MonthStatus month = calculator.month(List.of(), YearMonth.of(2028, 2), LocalDate.of(2028, 3, 5));
        assertEquals(29, month.daily().size());
        assertEquals(21, month.workingDays());
    }

    @Test
    void futureMonthIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.month(List.of(), YearMonth.of(2026, 10), LocalDate.of(2026, 9, 17)));
    }

    @Test
    void overviewHasTwelveMonthsOldestFirst() {
        List<MonthOverview> months = calculator.overview(List.of(row("2026-09-01", "AJI-1", 8)), LocalDate.of(2026, 9, 17));
        assertEquals(12, months.size());
        assertEquals("2025-10", months.get(0).yearMonth());
        assertEquals("2026-09", months.get(11).yearMonth());
        assertTrue(months.get(11).currentMonth());
        assertEquals(8.0, months.get(11).loggedHours(), 1e-9);
        assertEquals(1, months.get(11).worklogCount());
    }

    @Test
    void hoursOnSumsOneDay() {
        List<WorklogRow> rows = List.of(row("2026-09-17", "AJI-1", 2), row("2026-09-17", "AJI-2", 1.5),
                row("2026-09-16", "AJI-1", 8));
        assertEquals(3.5, WorklogCalculator.hoursOn(rows, LocalDate.of(2026, 9, 17)), 1e-9);
    }

    @Test
    void leaveDaysReduceMonthlyTargetAndAverageDivisor() {
        // 2026 szeptember: 22 munkanap; 2 nap szabadság (szept. 7–8., hétfő–kedd) + 1 hétvégi "szabadság" (nem számít)
        WorklogCalculator withLeave = calculator.withLeaves(Set.of(
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 12)));
        MonthStatus month = withLeave.month(List.of(), YearMonth.of(2026, 9), LocalDate.of(2026, 9, 30));
        assertEquals(20, month.workingDays());
        assertEquals(2, month.leaveDays());
        assertEquals(160.0, month.expectedHours(), 1e-9);
        assertTrue(month.daily().get(6).leave());   // szept. 7.
        assertFalse(month.daily().get(6).workday());
        assertFalse(month.daily().get(11).leave()); // szept. 12. szombat

        // Hét: 2026-09-07..13, 3 munkanapon 8 óra, 2 nap szabadság -> átlag 24/3 = 8
        List<WorklogRow> rows = List.of(row("2026-09-09", "AJI-1", 8), row("2026-09-10", "AJI-1", 8),
                row("2026-09-11", "AJI-1", 8));
        Summary week = withLeave.summarize(rows, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13));
        assertEquals(3, week.averageWorkingDays());
        assertEquals(2, week.leaveDays());
        assertEquals(8.0, week.dailyAverage(), 1e-9);
    }

    @Test
    void hoursLoggedOnLeaveDayStillCountInTotal() {
        WorklogCalculator withLeave = calculator.withLeaves(Set.of(LocalDate.of(2026, 9, 7)));
        Summary summary = withLeave.summarize(List.of(row("2026-09-07", "AJI-1", 2)),
                LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7));
        assertEquals(2.0, summary.totalHours(), 1e-9);
        assertNull(summary.dailyAverage());
    }
}
