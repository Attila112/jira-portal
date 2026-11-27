package hu.bplusn.portal.worklog;

import hu.bplusn.portal.auth.JiraAuth;
import hu.bplusn.portal.auth.PortalUser;
import hu.bplusn.portal.jira.JiraModels.WorklogRow;
import hu.bplusn.portal.worklog.LeaveStore.Leave;
import hu.bplusn.portal.worklog.SavedPeopleStore.SavedPerson;
import hu.bplusn.portal.worklog.WorklogModels.OverviewReport;
import hu.bplusn.portal.worklog.WorklogModels.WorklogReport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/worklog")
public class WorklogController {

    private final WorklogService service;
    private final SavedPeopleStore savedPeople;
    private final JiraAuth jiraAuth;
    private final LeaveStore leaveStore;

    public WorklogController(WorklogService service, SavedPeopleStore savedPeople, JiraAuth jiraAuth,
                             LeaveStore leaveStore) {
        this.service = service;
        this.savedPeople = savedPeople;
        this.jiraAuth = jiraAuth;
        this.leaveStore = leaveStore;
    }

    /** Időszakos vagy havi riport. Példa: /api/worklog/report?view=month&month=2026-11&projects=AJI,BNSD */
    @GetMapping("/report")
    public WorklogReport report(@AuthenticationPrincipal PortalUser me, HttpSession session,
                                @RequestParam(required = false) String user,
                                @RequestParam(defaultValue = "period") String view,
                                @RequestParam(required = false) Integer days,
                                @RequestParam(required = false) String month,
                                @RequestParam(required = false) String projects) {
        return service.report(jiraAuth.credentials(session), me, user, view, days, month, projects);
    }

    /** Az elmúlt 12 hónap összesítője. */
    @GetMapping("/overview")
    public OverviewReport overview(@AuthenticationPrincipal PortalUser me, HttpSession session,
                                   @RequestParam(required = false) String user,
                                   @RequestParam(required = false) String projects) {
        return service.overview(jiraAuth.credentials(session), me, user, projects);
    }

    /** CSV export a riport paramétereivel (a sorokat a szerver kéri le újra, nem a klienstől fogadja). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal PortalUser me, HttpSession session,
                                         @RequestParam(required = false) String user,
                                         @RequestParam(defaultValue = "period") String view,
                                         @RequestParam(required = false) Integer days,
                                         @RequestParam(required = false) String month,
                                         @RequestParam(required = false) String projects) {
        WorklogReport report = service.report(jiraAuth.credentials(session), me, user, view, days, month, projects);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Dátum;Issue;Összefoglaló;Projekt;Óra;Másodperc;Megjegyzés;Worklog ID\r\n");
        for (WorklogRow row : report.summary().rows()) {
            csv.append(String.join(";",
                    cell(row.date()), cell(row.issue()), cell(row.issueSummary()), cell(row.project()),
                    cell(String.format(Locale.forLanguageTag("hu-HU"), "%.2f", row.seconds() / 3600.0)),
                    cell(Long.toString(row.seconds())), cell(row.comment()), cell(row.worklogId())));
            csv.append("\r\n");
        }
        String fileName = "worklog_" + report.target().username().replaceAll("[^A-Za-z0-9._-]", "_")
                + "_" + report.startDate() + "_" + report.endDate() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** CSV cella: idézőjelezés + Excel formula-injection elleni védelem. */
    static String cell(String value) {
        String text = value == null ? "" : value;
        String stripped = text.stripLeading();
        if (stripped.startsWith("=") || stripped.startsWith("+") || stripped.startsWith("-") || stripped.startsWith("@")
                || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /* --- Szabadságnapok --- */

    /** Szabadságnapok: saját, vagy adminnak a megadott felhasználóé (csak olvasás). */
    @GetMapping("/leaves")
    public List<Leave> leaves(@AuthenticationPrincipal PortalUser me, @RequestParam(required = false) String user) {
        String target = WorklogService.resolveTarget(me, user);
        return leaveStore.list(target == null ? me.username() : target);
    }

    /** A saját szabadságnapok teljes listájának mentése. */
    @PutMapping("/leaves")
    public List<Leave> saveLeaves(@AuthenticationPrincipal PortalUser me, @RequestBody List<Leave> leaves) {
        return leaveStore.save(me.username(), leaves);
    }

    /* --- Mentett személyek (csak admin) --- */

    @GetMapping("/people")
    public List<SavedPerson> people(@AuthenticationPrincipal PortalUser me) {
        requireAdmin(me);
        return savedPeople.list(me.key());
    }

    @PutMapping("/people")
    public List<SavedPerson> savePeople(@AuthenticationPrincipal PortalUser me, @RequestBody List<SavedPerson> people) {
        requireAdmin(me);
        return savedPeople.save(me.key(), people);
    }

    private static void requireAdmin(PortalUser me) {
        if (!me.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ez a funkció csak adminisztrátoroknak érhető el.");
        }
    }
}
