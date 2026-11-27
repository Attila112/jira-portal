package hu.bplusn.portal.worklog;

import hu.bplusn.portal.jira.JiraModels.WorklogRow;

import java.util.List;
import java.util.Map;

/** A Worklog monitor API válaszai (a frontend src/types.ts fájlja ezeket tükrözi). */
public final class WorklogModels {

    private WorklogModels() {
    }

    /** Egy nap: workday = ledolgozandó munkanap, leave = szabadság (egyébként munkanap lenne). */
    public record DayTotal(String date, double hours, long seconds, boolean workday, boolean leave) {
    }

    /** Projekt vagy issue szerinti összesítés. */
    public record GroupTotal(String key, String label, double hours, long seconds, int worklogCount) {
    }

    public record Summary(
            long totalSeconds,
            double totalHours,
            int worklogCount,
            int issueCount,
            /* összes óra / munkanapok; null, ha az időszakban nincs munkanap */
            Double dailyAverage,
            int averageWorkingDays,
            /* szabadságnapok az időszakban (nem számítanak bele az átlagba) */
            int leaveDays,
            List<DayTotal> daily,
            List<GroupTotal> projects,
            List<GroupTotal> issues,
            List<WorklogRow> rows) {
    }

    public record MonthStatus(
            String yearMonth,
            String label,
            String startDate,
            String endDate,
            boolean currentMonth,
            int workingDays,
            int elapsedWorkingDays,
            /* a hónap szabadságnapjai (a workingDays már nélkülük számol) */
            int leaveDays,
            double expectedHours,
            double expectedToDateHours,
            double loggedHours,
            double remainingHours,
            double differenceToDateHours,
            double completionPercent,
            double toDateCompletionPercent,
            List<DayTotal> daily) {
    }

    public record MonthOverview(
            String yearMonth,
            String label,
            double loggedHours,
            double expectedHours,
            double completionPercent,
            double differenceHours,
            int workingDays,
            int leaveDays,
            int worklogCount,
            int issueCount,
            boolean currentMonth) {
    }

    public record Target(String username, String displayName, boolean self) {
    }

    public record Diagnostics(
            String jql,
            String scope,
            Integer searchedIssueCount,
            Long scannedWorklogCount,
            Map<String, Long> jiraTimings,
            Long jiraExecutionMs,
            long requestMs,
            int queriedDays) {
    }

    public record WorklogReport(
            String view,
            String startDate,
            String endDate,
            String today,
            int days,
            Target target,
            List<String> projects,
            double todayHours,
            Summary summary,
            MonthStatus month,
            Diagnostics diagnostics) {
    }

    public record OverviewReport(Target target, String today, List<MonthOverview> months, Diagnostics diagnostics) {
    }
}
