package hu.bplusn.portal;

import hu.bplusn.portal.auth.PortalUser;
import hu.bplusn.portal.jira.JiraGateway;
import hu.bplusn.portal.jira.JiraGateway.LoginResult;
import hu.bplusn.portal.jira.MockData;
import hu.bplusn.portal.worklog.WorklogModels.OverviewReport;
import hu.bplusn.portal.worklog.WorklogModels.WorklogReport;
import hu.bplusn.portal.worklog.WorklogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A teljes Spring kontextus betöltése mock Jirával + a worklog szolgáltatás végigfuttatása. */
@SpringBootTest(properties = "portal.data-dir=target/test-data")
@ActiveProfiles("mock")
class PortalApplicationTests {

    @Autowired
    private JiraGateway jira;

    @Autowired
    private WorklogService worklogService;

    @Test
    void periodAndMonthReportsWithMockJira() {
        LoginResult login = jira.login("attila.gonda@bplusn.hu", MockData.MOCK_PASSWORD);
        PortalUser me = new PortalUser(login.me().user().key(), login.me().user().username(),
                login.me().user().displayName(), login.me().user().email(), login.me().admin());
        assertFalse(me.admin());

        WorklogReport period = worklogService.report(login.credentials(), me, null, "period", 14, null, null);
        assertEquals(14, period.summary().daily().size());
        assertTrue(period.target().self());

        WorklogReport month = worklogService.report(login.credentials(), me, null, "month", null, null, "AJI");
        assertEquals("month", month.view());
        assertTrue(month.summary().rows().stream().allMatch(row -> "AJI".equals(row.project())));

        OverviewReport overview = worklogService.overview(login.credentials(), me, null, null);
        assertEquals(12, overview.months().size());
    }

    @Test
    void nonAdminCannotQueryOthers() {
        LoginResult login = jira.login("attila.gonda@bplusn.hu", MockData.MOCK_PASSWORD);
        PortalUser me = new PortalUser("k", "attila.gonda@bplusn.hu", "Gonda Attila", null, false);
        assertThrows(ResponseStatusException.class,
                () -> worklogService.report(login.credentials(), me, "valaki.mas@bplusn.hu", "period", 7, null, null));
    }

    @Test
    void adminCanQueryOthers() {
        LoginResult login = jira.login("admin", MockData.MOCK_PASSWORD);
        PortalUser me = new PortalUser("k", "admin", "Demo Admin", null, true);
        WorklogReport report = worklogService.report(login.credentials(), me, "kerekes.tamas@bplusn.hu", "period", 7,
                null, null);
        assertEquals("Kerekes Tamás", report.target().displayName());
        assertFalse(report.target().self());
    }
}
