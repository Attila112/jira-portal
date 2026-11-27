package hu.bplusn.portal.auth;

import hu.bplusn.portal.jira.JiraCredentials;
import hu.bplusn.portal.jira.JiraGateway;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * A munkamenethez tartozó Jira-hitelesítés lekérése a controllerekben.
 * OAuth belépésnél a lejárat előtt álló access tokent automatikusan frissíti.
 */
@Component
public class JiraAuth {

    private final JiraGateway jira;

    public JiraAuth(JiraGateway jira) {
        this.jira = jira;
    }

    public JiraCredentials credentials(HttpSession session) {
        JiraCredentials current = JiraSession.require(session);
        if (!current.expiresWithin(60, Instant.now())) {
            return current;
        }
        // Egyszerre csak egy frissítés fusson (a refresh token egyszer használható lehet).
        synchronized (this) {
            current = JiraSession.require(session);
            JiraCredentials refreshed = jira.refreshIfNeeded(current);
            if (refreshed != current) {
                JiraSession.store(session, refreshed);
            }
            return refreshed;
        }
    }
}
