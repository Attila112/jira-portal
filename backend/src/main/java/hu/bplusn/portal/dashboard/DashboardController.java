package hu.bplusn.portal.dashboard;

import hu.bplusn.portal.auth.JiraAuth;
import hu.bplusn.portal.jira.JiraGateway;
import hu.bplusn.portal.jira.JiraModels.IssuesResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final JiraGateway jira;
    private final JiraAuth jiraAuth;

    public DashboardController(JiraGateway jira, JiraAuth jiraAuth) {
        this.jira = jira;
        this.jiraAuth = jiraAuth;
    }

    /** A bejelentkezett felhasználó jegyei (portalMyIssues, currentUser() JQL). */
    @GetMapping("/issues")
    public IssuesResponse issues(HttpSession session) {
        return jira.myIssues(jiraAuth.credentials(session));
    }
}
