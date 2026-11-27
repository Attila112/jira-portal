package hu.bplusn.portal.jira;

import hu.bplusn.portal.jira.JiraModels.FieldNames;
import hu.bplusn.portal.jira.JiraModels.Issue;
import hu.bplusn.portal.jira.JiraModels.IssuesResponse;
import hu.bplusn.portal.jira.JiraModels.JiraUser;
import hu.bplusn.portal.jira.JiraModels.MeResponse;
import hu.bplusn.portal.jira.JiraModels.ServerInfo;
import hu.bplusn.portal.jira.JiraModels.WorklogRow;
import hu.bplusn.portal.jira.JiraModels.WorklogsResponse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Determinisztikus, szimulált Jira-adatok a "mock" profilhoz és a tesztekhez.
 * Ugyanarra a felhasználóra és napra mindig ugyanazt adja vissza.
 */
public final class MockData {

    public static final String MOCK_BASE_URL = "https://jira.example.invalid";
    public static final String MOCK_PASSWORD = "demo";

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private record MockIssue(String key, String summary) {
        String project() {
            return key.substring(0, key.indexOf('-'));
        }
    }

    private static final List<MockIssue> WORK_ISSUES = List.of(
            new MockIssue("AJI-1042", "Ügyfélportál – számlaletöltés hibája"),
            new MockIssue("AJI-1057", "Jogosultságkezelés átdolgozása"),
            new MockIssue("AJI-1063", "Riport export Excel formátumban"),
            new MockIssue("BNSD-2311", "ScriptRunner listener a státuszváltáshoz"),
            new MockIssue("BNSD-2318", "Assets objektumok szinkronizálása"),
            new MockIssue("BNSD-2325", "SLA mezők javítása a BNSZK projektben"),
            new MockIssue("BNSD-2340", "Automatizálási szabály felülvizsgálata"),
            new MockIssue("SP-418", "Napi stand-up és egyeztetések"));

    private static final List<String> COMMENTS = List.of(
            "Fejlesztés", "Hibakeresés és javítás", "Kódáttekintés", "Egyeztetés az ügyféllel",
            "Tesztelés", "Dokumentáció frissítése", "Groovy script módosítása", "Deploy és ellenőrzés", "");

    private MockData() {
    }

    public static JiraUser user(String username) {
        String normalized = username == null ? "" : username.trim();
        String display = switch (normalized.toLowerCase(Locale.ROOT)) {
            case "admin" -> "Demo Admin";
            case "attila.gonda@bplusn.hu" -> "Gonda Attila";
            case "kerekes.tamas@bplusn.hu" -> "Kerekes Tamás";
            default -> {
                String local = normalized.contains("@") ? normalized.substring(0, normalized.indexOf('@')) : normalized;
                StringBuilder builder = new StringBuilder();
                for (String part : local.split("[._-]+")) {
                    if (!part.isEmpty()) {
                        builder.append(builder.isEmpty() ? "" : " ")
                                .append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
                    }
                }
                yield builder.isEmpty() ? "Demo felhasználó" : builder.toString();
            }
        };
        return new JiraUser("key-" + normalized.toLowerCase(Locale.ROOT), normalized, display,
                normalized.contains("@") ? normalized : normalized + "@example.invalid", true);
    }

    public static boolean isAdmin(String username) {
        return username != null && username.trim().equalsIgnoreCase("admin");
    }

    public static ServerInfo server(ZonedDateTime now) {
        return new ServerInfo(MOCK_BASE_URL, "10.3.25 (mock)", now.getZone().getId(), now.format(ISO_OFFSET));
    }

    public static MeResponse me(String username, ZonedDateTime now) {
        return new MeResponse(user(username), isAdmin(username), server(now));
    }

    /* ------------------------------------------------------------------ */
    /* Worklogok                                                           */
    /* ------------------------------------------------------------------ */

    public static WorklogsResponse worklogs(JiraUser target, int days, List<String> projects, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate start = today.minusDays(days - 1L);
        List<WorklogRow> rows = new ArrayList<>();
        int worklogId = 0;
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            Random random = new Random(31L * target.username().toLowerCase(Locale.ROOT).hashCode() + date.toEpochDay());
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            double hours;
            if (weekend) {
                hours = random.nextInt(12) == 0 ? 2 + random.nextInt(3) : 0;
            } else {
                int roll = random.nextInt(100);
                hours = roll < 7 ? 0 : roll < 15 ? 4 : roll < 25 ? 6.5 : roll < 70 ? 8 : roll < 85 ? 7.5 : 9;
            }
            if (date.equals(today)) {
                hours = Math.min(hours, Math.max(0, now.getHour() - 8));
            }
            long remaining = Math.round(hours * 3600);
            int parts = remaining == 0 ? 0 : 1 + random.nextInt(3);
            for (int part = 0; part < parts && remaining > 0; part++) {
                long seconds = part == parts - 1 ? remaining : Math.max(1800, (remaining / (parts - part)) / 900 * 900);
                seconds = Math.min(seconds, remaining);
                remaining -= seconds;
                MockIssue issue = WORK_ISSUES.get(random.nextInt(WORK_ISSUES.size()));
                if (!projects.isEmpty() && !projects.contains(issue.project())) {
                    continue;
                }
                rows.add(new WorklogRow(date.toString(), issue.key(), issue.summary(), issue.project(), seconds,
                        COMMENTS.get(random.nextInt(COMMENTS.size())), "mock-" + date + "-" + (++worklogId)));
            }
        }
        rows.sort(Comparator.comparing(WorklogRow::date).reversed().thenComparing(WorklogRow::issue));
        return new WorklogsResponse(server(now), target, days, start.toString(), today.toString(), projects,
                "authored", "worklogAuthor = \"" + target.username() + "\" AND worklogDate >= \"" + start + "\" (mock)",
                WORK_ISSUES.size(), (long) rows.size() * 3, rows.size(), rows,
                Map.of("issueSearchMs", 42L, "worklogProcessingMs", 87L), 131L);
    }

    /* ------------------------------------------------------------------ */
    /* Dashboard jegyek                                                    */
    /* ------------------------------------------------------------------ */

    public static IssuesResponse issues(JiraUser user, ZonedDateTime now) {
        String me = user.displayName();
        LocalDate today = now.toLocalDate();
        List<Issue> issues = new ArrayList<>();
        issues.add(issue("AJI-1042", "Ügyfélportál – számlaletöltés hibája", "Hiba", "Folyamatban", "indeterminate",
                "Magas", me, "Kerekes Tamás", now.minusHours(2), today.plusDays(2), null, List.of("responsible")));
        issues.add(issue("AJI-1057", "Jogosultságkezelés átdolgozása", "Fejlesztés", "Folyamatban", "indeterminate",
                "Közepes", me, null, now.minusHours(26), today.plusDays(9), null, List.of("responsible")));
        issues.add(issue("AJI-1063", "Riport export Excel formátumban", "Fejlesztés", "Új", "new",
                "Alacsony", me, null, now.minusDays(3), null, null, List.of("responsible")));
        issues.add(issue("BNSD-2311", "ScriptRunner listener a státuszváltáshoz", "Szolgáltatáskérés", "Várakozik", "indeterminate",
                "Közepes", me, null, now.minusDays(1), today.minusDays(1), null, List.of("responsible")));
        issues.add(issue("BNSD-2318", "Assets objektumok szinkronizálása", "Szolgáltatáskérés", "Tesztelésre vár", "indeterminate",
                "Magas", "Kerekes Tamás", me, now.minusHours(5), today.plusDays(1), null, List.of("tester")));
        issues.add(issue("BNSD-2325", "SLA mezők javítása a BNSZK projektben", "Hiba", "Tesztelés alatt", "indeterminate",
                "Kritikus", "Szabó Péter", me, now.minusMinutes(40), today, null, List.of("tester")));
        issues.add(issue("BNSD-2329", "Workflow validátor hibaüzenete", "Hiba", "Done", "done",
                "Közepes", me, null, now.minusDays(2), null, now.minusDays(2), List.of("responsible", "resolvedRecently")));
        issues.add(issue("AJI-1031", "Felhasználói visszajelzés űrlap", "Fejlesztés", "Sikeres teszt", "done",
                "Alacsony", "Kiss Anna", me, now.minusDays(4), null, now.minusDays(4), List.of("tester", "resolvedRecently")));
        issues.add(issue("SP-418", "Napi stand-up és egyeztetések", "Feladat", "Folyamatban", "indeterminate",
                "Alacsony", me, null, now.minusDays(6), null, null, List.of("responsible")));

        Issue parent = issues.get(1);
        issues.add(new Issue("AJI-1058", "Szerepkör-mátrix dokumentálása", "AJI", "AJI projekt", "Részfeladat", true,
                parent.key(), parent.summary(), "Új", "new", "Közepes", null, "Kiss Anna", List.of("Kiss Anna"),
                List.of(), iso(now.minusDays(2)), iso(now.minusHours(30)), null, null, List.of("subtask")));
        issues.add(new Issue("AJI-1059", "Jogosultság migrációs script", "AJI", "AJI projekt", "Részfeladat", true,
                parent.key(), parent.summary(), "Folyamatban", "indeterminate", "Közepes", null, me, List.of(me),
                List.of(), iso(now.minusDays(2)), iso(now.minusHours(3)), null, today.plusDays(5).toString(),
                List.of("responsible")));

        issues.sort(Comparator.comparing(Issue::updated).reversed());
        return new IssuesResponse(server(now), new FieldNames("Munka felelőse", "Tesztelő"), issues.size(), 300, issues,
                "(\"Munka felelőse\" = currentUser() ...) (mock)", 64L);
    }

    private static Issue issue(String key, String summary, String type, String status, String category, String priority,
                               String responsible, String tester, ZonedDateTime updated, LocalDate due,
                               ZonedDateTime resolved, List<String> reasons) {
        String project = key.substring(0, key.indexOf('-'));
        return new Issue(key, summary, project, project + " projekt", type, false, null, null, status, category, priority,
                resolved == null ? null : "Kész", responsible, responsible == null ? List.of() : List.of(responsible),
                tester == null ? List.of() : List.of(tester), iso(updated.minusDays(10)), iso(updated),
                resolved == null ? null : iso(resolved), due == null ? null : due.toString(), reasons);
    }

    private static String iso(ZonedDateTime value) {
        return value.format(ISO_OFFSET);
    }
}
