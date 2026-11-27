package hu.bplusn.portal.worklog;

import hu.bplusn.portal.auth.PortalUser;
import hu.bplusn.portal.config.PortalProperties;
import hu.bplusn.portal.jira.JiraCredentials;
import hu.bplusn.portal.jira.JiraGateway;
import hu.bplusn.portal.jira.JiraModels.JiraUser;
import hu.bplusn.portal.jira.JiraModels.WorklogsResponse;
import hu.bplusn.portal.worklog.WorklogModels.Diagnostics;
import hu.bplusn.portal.worklog.WorklogModels.MonthStatus;
import hu.bplusn.portal.worklog.WorklogModels.OverviewReport;
import hu.bplusn.portal.worklog.WorklogModels.Summary;
import hu.bplusn.portal.worklog.WorklogModels.Target;
import hu.bplusn.portal.worklog.WorklogModels.WorklogReport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** A Worklog monitor üzleti logikája: paraméterek ellenőrzése, Jira-lekérés, összesítés. */
@Service
public class WorklogService {

    public static final int MAX_DAYS = 366;

    private final JiraGateway jira;
    private final LeaveStore leaveStore;
    private final WorklogCalculator calculator;
    private final ZoneId zone;

    public WorklogService(JiraGateway jira, LeaveStore leaveStore, PortalProperties properties) {
        this.jira = jira;
        this.leaveStore = leaveStore;
        PortalProperties.Worklog worklog = properties.worklog();
        this.calculator = new WorklogCalculator(
                WorkCalendar.fromStrings(worklog.holidays(), worklog.extraWorkdays()), worklog.hoursPerDay());
        this.zone = ZoneId.of(worklog.zone());
    }

    /**
     * @param view  "period" (utolsó N nap) vagy "month" (választott hónap)
     * @param month YYYY-MM, csak month nézetben (üres = aktuális hónap)
     */
    public WorklogReport report(JiraCredentials credentials, PortalUser me, String targetUser, String view,
                                Integer days, String month, String projects) {
        long started = System.nanoTime();
        String user = resolveTarget(me, targetUser);
        List<String> projectKeys = parseProjects(projects);
        LocalDate localToday = LocalDate.now(zone);
        String mode = view == null || view.isBlank() ? "period" : view.trim().toLowerCase(Locale.ROOT);

        int periodDays;
        YearMonth selectedMonth;
        int queryDays;
        if (mode.equals("period")) {
            periodDays = days == null ? 14 : days;
            if (periodDays < 1 || periodDays > MAX_DAYS) {
                throw badRequest("A napok száma 1 és " + MAX_DAYS + " között lehet.");
            }
            selectedMonth = YearMonth.from(localToday);
            // Egy lekéréssel az időszakot és az aktuális hónap állapotát is lefedjük.
            queryDays = Math.min(MAX_DAYS, Math.max(periodDays, localToday.getDayOfMonth()));
        } else if (mode.equals("month")) {
            selectedMonth = parseMonth(month, localToday);
            queryDays = (int) ChronoUnit.DAYS.between(selectedMonth.atDay(1), localToday) + 1;
            periodDays = 0;
        } else {
            throw badRequest("Ismeretlen nézet: " + view);
        }

        WorklogsResponse response = jira.worklogs(credentials, user, queryDays, projectKeys);
        LocalDate today = jiraToday(response, localToday);
        List<hu.bplusn.portal.jira.JiraModels.WorklogRow> rows = response.rows() == null ? List.of() : response.rows();
        WorklogCalculator calculator = calculatorFor(response, me);

        MonthStatus monthStatus;
        Summary summary;
        LocalDate start;
        LocalDate end;
        if (mode.equals("period")) {
            start = today.minusDays(periodDays - 1L);
            end = today;
            summary = calculator.summarize(rows, start, end);
            monthStatus = calculator.month(rows, YearMonth.from(today), today);
        } else {
            if (selectedMonth.isAfter(YearMonth.from(today))) {
                selectedMonth = YearMonth.from(today);
            }
            monthStatus = calculator.month(rows, selectedMonth, today);
            start = selectedMonth.atDay(1);
            end = LocalDate.parse(monthStatus.endDate());
            summary = calculator.summarize(rows, start, end);
            periodDays = (int) ChronoUnit.DAYS.between(start, end) + 1;
        }

        return new WorklogReport(mode, start.toString(), end.toString(), today.toString(), periodDays,
                target(response.targetUser(), me), projectKeys, WorklogCalculator.hoursOn(rows, today),
                summary, monthStatus, diagnostics(response, started, queryDays));
    }

    /** Az elmúlt 12 hónap összesítője egyetlen 366 napos lekérésből. */
    public OverviewReport overview(JiraCredentials credentials, PortalUser me, String targetUser, String projects) {
        long started = System.nanoTime();
        String user = resolveTarget(me, targetUser);
        List<String> projectKeys = parseProjects(projects);
        WorklogsResponse response = jira.worklogs(credentials, user, MAX_DAYS, projectKeys);
        LocalDate today = jiraToday(response, LocalDate.now(zone));
        List<hu.bplusn.portal.jira.JiraModels.WorklogRow> rows = response.rows() == null ? List.of() : response.rows();
        WorklogCalculator calculator = calculatorFor(response, me);
        return new OverviewReport(target(response.targetUser(), me), today.toString(),
                calculator.overview(rows, today), diagnostics(response, started, MAX_DAYS));
    }

    /* ------------------------------------------------------------------ */

    /** A lekérdezett felhasználó szabadságnapjaival számoló kalkulátor. */
    private WorklogCalculator calculatorFor(WorklogsResponse response, PortalUser me) {
        String owner = response.targetUser() != null && response.targetUser().username() != null
                ? response.targetUser().username() : me.username();
        return calculator.withLeaves(leaveStore.dates(owner));
    }

    /** null = saját magam (a Jira a hívó felhasználót használja). */
    static String resolveTarget(PortalUser me, String targetUser) {
        if (targetUser == null || targetUser.isBlank() || targetUser.trim().equalsIgnoreCase(me.username())) {
            return null;
        }
        if (!me.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Más felhasználó worklogjait csak adminisztrátor kérheti le.");
        }
        String value = targetUser.trim();
        if (value.length() > 254) {
            throw badRequest("Túl hosszú felhasználónév.");
        }
        return value;
    }

    static List<String> parseProjects(String projects) {
        if (projects == null || projects.isBlank()) {
            return List.of();
        }
        List<String> keys = Arrays.stream(projects.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        for (String key : keys) {
            if (!key.matches("[A-Z][A-Z0-9_]*")) {
                throw badRequest("Érvénytelen projektkulcs: " + key);
            }
        }
        return keys;
    }

    static YearMonth parseMonth(String value, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (value == null || value.isBlank()) {
            return current;
        }
        YearMonth month;
        try {
            month = YearMonth.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw badRequest("A hónap formátuma YYYY-MM legyen.");
        }
        if (month.isAfter(current)) {
            throw badRequest("Jövőbeli hónap még nem kérhető le.");
        }
        if (month.isBefore(current.minusMonths(11))) {
            throw badRequest("Legfeljebb az elmúlt 12 hónap kérhető le.");
        }
        return month;
    }

    private static LocalDate jiraToday(WorklogsResponse response, LocalDate fallback) {
        if (response.server() != null && response.server().serverTime() != null) {
            try {
                return OffsetDateTime.parse(response.server().serverTime()).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // marad a helyi dátum
            }
        }
        return fallback;
    }

    private static Target target(JiraUser jiraUser, PortalUser me) {
        if (jiraUser == null) {
            return new Target(me.username(), me.displayName(), true);
        }
        return new Target(jiraUser.username(), jiraUser.displayName(),
                jiraUser.username() != null && jiraUser.username().equalsIgnoreCase(me.username()));
    }

    private static Diagnostics diagnostics(WorklogsResponse response, long startedNanos, int queriedDays) {
        return new Diagnostics(response.jql(), response.scope(), response.searchedIssueCount(),
                response.scannedWorklogCount(), response.timings(), response.totalExecutionMs(),
                (System.nanoTime() - startedNanos) / 1_000_000, queriedDays);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
