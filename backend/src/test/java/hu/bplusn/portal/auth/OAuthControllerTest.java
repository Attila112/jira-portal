package hu.bplusn.portal.auth;

import hu.bplusn.portal.jira.JiraCredentials;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthControllerTest {

    @Test
    void pkceChallengeMatchesRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                OAuthController.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void tokenExpiryWindow() {
        Instant now = Instant.parse("2026-11-24T12:00:00Z");
        JiraCredentials credentials = JiraCredentials.oauth("a", "r", now.plusSeconds(90));
        assertFalse(credentials.expiresWithin(60, now));
        assertTrue(credentials.expiresWithin(120, now));
        assertFalse(JiraCredentials.session("u", "c").expiresWithin(60, now));
    }
}
