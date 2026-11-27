package hu.bplusn.portal.jira;

import hu.bplusn.portal.config.PortalProperties;
import hu.bplusn.portal.jira.JiraException.Kind;
import hu.bplusn.portal.jira.JiraModels.IssuesResponse;
import hu.bplusn.portal.jira.JiraModels.JiraUser;
import hu.bplusn.portal.jira.JiraModels.WorklogsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Jira nélküli fejlesztéshez: "mock" profillal indítva ez kerül használatba.
 * Belépés: bármilyen felhasználónév + "demo" jelszó. Az "admin" felhasználó admin jogot kap.
 */
@Component
@Profile("mock")
public class MockJiraGateway implements JiraGateway {

    private static final Logger log = LoggerFactory.getLogger(MockJiraGateway.class);

    private final ZoneId zone;

    public MockJiraGateway(PortalProperties properties) {
        this.zone = ZoneId.of(properties.worklog().zone());
        log.warn("MOCK Jira gateway aktív: szimulált adatok, belépés bármely felhasználónévvel és '{}' jelszóval.",
                MockData.MOCK_PASSWORD);
    }

    @Override
    public LoginResult login(String username, String password) {
        simulateLatency();
        if (!MockData.MOCK_PASSWORD.equals(password)) {
            throw new JiraException(Kind.UNAUTHORIZED, "Hibás Jira-felhasználónév vagy jelszó. (Mock módban a jelszó: demo)");
        }
        return new LoginResult(JiraCredentials.session(username, "MOCKSESSION=1"), MockData.me(username, now()));
    }

    @Override
    public boolean oauthEnabled() {
        return false;
    }

    @Override
    public String oauthAuthorizeUrl(String state, String codeChallenge) {
        throw new JiraException(Kind.BAD_REQUEST, "Mock módban a Belépés Jirával nem érhető el.");
    }

    @Override
    public LoginResult oauthLogin(String code, String codeVerifier) {
        throw new JiraException(Kind.BAD_REQUEST, "Mock módban a Belépés Jirával nem érhető el.");
    }

    @Override
    public JiraCredentials refreshIfNeeded(JiraCredentials credentials) {
        return credentials;
    }

    @Override
    public void logout(JiraCredentials credentials) {
        // nincs teendő
    }

    @Override
    public IssuesResponse myIssues(JiraCredentials credentials) {
        simulateLatency();
        return MockData.issues(MockData.user(credentials.username()), now());
    }

    @Override
    public WorklogsResponse worklogs(JiraCredentials credentials, String targetUser, int days, List<String> projects) {
        simulateLatency();
        String self = credentials.username();
        JiraUser target = MockData.user(self);
        if (targetUser != null && !targetUser.isBlank() && !targetUser.trim().equalsIgnoreCase(self)) {
            if (!MockData.isAdmin(self)) {
                throw new JiraException(Kind.FORBIDDEN,
                        "Más felhasználó worklogjait csak a(z) jira-administrators csoport tagjai kérhetik le.");
            }
            target = MockData.user(targetUser);
        }
        return MockData.worklogs(target, days, projects, now());
    }

    @Override
    public String baseUrl() {
        return MockData.MOCK_BASE_URL;
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(zone);
    }

    private static void simulateLatency() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
