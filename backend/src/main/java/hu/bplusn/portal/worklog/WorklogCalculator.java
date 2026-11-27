package hu.bplusn.portal.worklog;

import hu.bplusn.portal.jira.JiraModels.WorklogRow;
import hu.bplusn.portal.worklog.WorklogModels.DayTotal;
import hu.bplusn.portal.worklog.WorklogModels.GroupTotal;
import hu.bplusn.portal.worklog.WorklogModels.MonthOverview;
import hu.bplusn.portal.worklog.WorklogModels.MonthStatus;
import hu.bplusn.portal.worklog.WorklogModels.Summary;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A worklog-összesítések (a Python változat logikája Java-ban, munkaszüneti napokkal kiegészítve).
 *
 * <p><b>Munkanapi átlag</b> = az időszak összes logolt órája / az időszak munkanapjainak száma.
 * A hétvégi (és ünnepnapi) logolás az összes órában benne van, de az osztót nem növeli.</p>
 *
 * <p>A felhasználó szabadságnapjai nem munkanapok: kiesnek a havi keretből és az átlag osztójából,
 * de az ezeken a napokon esetleg logolt órák az összesben benne maradnak.</p>
 *
 * <p><b>Havi keret</b> = munkanapok × napi óraszám. Folyamatban lévő hónapnál az "eddig elvárt"
 * a mai napig eltelt munkanapokból számol, lezárt hónapnál a teljes hónapból.</p>
 */
public final class WorklogCalculator {

    private static final String[] HU_MONTHS = {"január", "február", "március", "április", "május", "június",
            "július", "augusztus", "szeptember", "október", "november", "december"};

    private final WorkCalendar calendar;
    private final double hoursPerDay;

    public WorklogCalculator(WorkCalendar calendar, double hoursPerDay) {
        this.calendar = calendar;
        this.hoursPerDay = hoursPerDay;
    }

    /** Ugyanez a számoló egy felhasználó szabadságnapjaival. */
    public WorklogCalculator withLeaves(java.util.Collection<LocalDate> leaveDays) {
        return new WorklogCalculator(calendar.withLeaves(leaveDays), hoursPerDay);
    }

    public static String monthLabel(YearMonth month) {
        return month.getYear() + ". " + HU_MONTHS[month.getMonthValue() - 1];
    }

    /** Összesítés a [start, end] intervallumra (a kívül eső sorokat kiszűri). */
    public Summary summarize(List<WorklogRow> rows, LocalDate start, LocalDate end) {
        List<WorklogRow> filtered = new ArrayList<>();
        for (WorklogRow row : rows) {
            LocalDate date = parseDate(row.date());
            if (date != null && !date.isBefore(start) && !date.isAfter(end)) {
                filtered.add(row);
            }
        }
        filtered.sort(Comparator.comparing(WorklogRow::date).reversed()
                .thenComparing(row -> row.issue() == null ? "" : row.issue()));

        long totalSeconds = 0;
        Map<String, Long> dailySeconds = new HashMap<>();
        Map<String, GroupAccumulator> projects = new LinkedHashMap<>();
        Map<String, GroupAccumulator> issues = new LinkedHashMap<>();
        for (WorklogRow row : filtered) {
            long seconds = Math.max(0, row.seconds());
            totalSeconds += seconds;
            dailySeconds.merge(row.date(), seconds, Long::sum);
            String project = row.project() == null ? "" : row.project();
            String issue = row.issue() == null ? "" : row.issue();
            projects.computeIfAbsent(project, key -> new GroupAccumulator(key, key)).add(seconds);
            issues.computeIfAbsent(issue, key -> new GroupAccumulator(key, row.issueSummary())).add(seconds);
        }

        List<DayTotal> daily = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            long seconds = dailySeconds.getOrDefault(date.toString(), 0L);
            daily.add(new DayTotal(date.toString(), seconds / 3600.0, seconds, calendar.isWorkday(date),
                    calendar.isLeave(date)));
        }

        double totalHours = totalSeconds / 3600.0;
        int workdays = calendar.workdays(start, end);
        return new Summary(totalSeconds, totalHours, filtered.size(), issues.size(),
                workdays > 0 ? totalHours / workdays : null, workdays, calendar.leaveDays(start, end),
                daily, sortedGroups(projects), sortedGroups(issues), filtered);
    }

    /** Egy hónap állapota a havi kerethez képest. */
    public MonthStatus month(List<WorklogRow> rows, YearMonth month, LocalDate today) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        if (first.isAfter(today)) {
            throw new IllegalArgumentException("Jövőbeli hónap még nem kérhető le.");
        }
        LocalDate effectiveEnd = last.isAfter(today) ? today : last;
        Summary summary = summarize(rows, first, effectiveEnd);

        int workingDays = calendar.workdays(first, last);
        int elapsedWorkingDays = calendar.workdays(first, effectiveEnd);
        double expected = workingDays * hoursPerDay;
        double expectedToDate = elapsedWorkingDays * hoursPerDay;
        double logged = summary.totalHours();
        return new MonthStatus(
                month.toString(),
                monthLabel(month),
                first.toString(),
                effectiveEnd.toString(),
                YearMonth.from(today).equals(month),
                workingDays,
                elapsedWorkingDays,
                calendar.leaveDays(first, last),
                expected,
                expectedToDate,
                logged,
                Math.max(expected - logged, 0),
                logged - expectedToDate,
                expected > 0 ? logged / expected * 100 : 0,
                expectedToDate > 0 ? logged / expectedToDate * 100 : 0,
                summary.daily());
    }

    /** Az elmúlt 12 hónap (a mostanival együtt), a legrégebbivel kezdve. */
    public List<MonthOverview> overview(List<WorklogRow> rows, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        List<MonthOverview> months = new ArrayList<>();
        for (int offset = 11; offset >= 0; offset--) {
            YearMonth month = current.minusMonths(offset);
            MonthStatus status = month(rows, month, today);
            Summary summary = summarize(rows, month.atDay(1), LocalDate.parse(status.endDate()));
            months.add(new MonthOverview(
                    status.yearMonth(),
                    status.label(),
                    status.loggedHours(),
                    status.expectedHours(),
                    status.completionPercent(),
                    status.loggedHours() - status.expectedHours(),
                    status.workingDays(),
                    status.leaveDays(),
                    summary.worklogCount(),
                    summary.issueCount(),
                    status.currentMonth()));
        }
        return months;
    }

    /** A megadott nap összes logolása órában. */
    public static double hoursOn(List<WorklogRow> rows, LocalDate day) {
        String key = day.toString();
        return rows.stream().filter(row -> key.equals(row.date())).mapToLong(WorklogRow::seconds).sum() / 3600.0;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static List<GroupTotal> sortedGroups(Map<String, GroupAccumulator> groups) {
        return groups.values().stream()
                .sorted(Comparator.comparingLong((GroupAccumulator group) -> group.seconds).reversed()
                        .thenComparing(group -> group.key))
                .map(group -> new GroupTotal(group.key, group.label, group.seconds / 3600.0, group.seconds, group.count))
                .toList();
    }

    private static final class GroupAccumulator {
        private final String key;
        private final String label;
        private long seconds;
        private int count;

        private GroupAccumulator(String key, String label) {
            this.key = key;
            this.label = label == null || label.isBlank() ? key : label;
        }

        private void add(long value) {
            seconds += value;
            count++;
        }
    }
}
