package hu.bplusn.portal.auth;

import hu.bplusn.portal.jira.JiraCredentials;
import hu.bplusn.portal.jira.JiraException;
import jakarta.servlet.http.HttpSession;

/** A Jira-hitelesítés tárolása a szerveroldali HTTP-munkamenetben. */
public final class JiraSession {

    private static final String ATTRIBUTE = "portal.jiraCredentials";

    private JiraSession() {
    }

    public static void store(HttpSession session, JiraCredentials credentials) {
        session.setAttribute(ATTRIBUTE, credentials);
    }

    public static JiraCredentials require(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(ATTRIBUTE);
        if (value instanceof JiraCredentials credentials) {
            return credentials;
        }
        throw new JiraException(JiraException.Kind.UNAUTHORIZED, "A munkamenet lejárt. Jelentkezz be újra.");
    }

    public static JiraCredentials find(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(ATTRIBUTE);
        return value instanceof JiraCredentials credentials ? credentials : null;
    }
}
