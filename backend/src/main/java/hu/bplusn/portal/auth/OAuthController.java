package hu.bplusn.portal.auth;

import hu.bplusn.portal.config.PortalProperties;
import hu.bplusn.portal.jira.JiraException;
import hu.bplusn.portal.jira.JiraGateway;
import hu.bplusn.portal.jira.JiraGateway.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * "Belépés Jirával": OAuth 2.0 authorization code flow PKCE-vel.
 * A Jira a céges SSO-val (Entra ID) lépteti be a felhasználót, a portál csak a Jira tokenjét kapja meg.
 */
@Controller
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    private static final String STATE = "portal.oauth.state";
    private static final String VERIFIER = "portal.oauth.verifier";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JiraGateway jira;
    private final SessionStarter sessionStarter;
    private final PortalProperties properties;

    public OAuthController(JiraGateway jira, SessionStarter sessionStarter, PortalProperties properties) {
        this.jira = jira;
        this.sessionStarter = sessionStarter;
        this.properties = properties;
    }

    /** 1. lépés: a böngészőt a Jira engedélyező oldalára küldjük. */
    @GetMapping("/api/auth/oauth/start")
    public void start(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!jira.oauthEnabled()) {
            redirectToLogin(response, "A Belépés Jirával nincs beállítva.");
            return;
        }
        String state = randomToken(24);
        String verifier = randomToken(48);
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE, state);
        session.setAttribute(VERIFIER, verifier);
        response.sendRedirect(jira.oauthAuthorizeUrl(state, challenge(verifier)));
    }

    /** 2. lépés: a Jira ide irányít vissza a kóddal (ez az Application Link Redirect URL-je). */
    @GetMapping("/login/oauth2/code/jira")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         @RequestParam(required = false) String error,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(STATE);
        String verifier = session == null ? null : (String) session.getAttribute(VERIFIER);
        if (session != null) {
            session.removeAttribute(STATE);
            session.removeAttribute(VERIFIER);
        }

        if (error != null) {
            redirectToLogin(response, "access_denied".equals(error)
                    ? "A belépést a Jira engedélyező oldalán elutasítottad."
                    : "A Jira-belépés megszakadt (" + error + ").");
            return;
        }
        if (code == null || state == null || expectedState == null || verifier == null
                || !MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
            redirectToLogin(response, "Érvénytelen vagy lejárt belépési kérés. Próbáld újra.");
            return;
        }

        try {
            LoginResult result = jira.oauthLogin(code, verifier);
            sessionStarter.start(result, "Jira OAuth", request, response);
            response.sendRedirect(properties.frontendUrl() + "/");
        } catch (JiraException exception) {
            log.warn("Jira OAuth belépés sikertelen: {}", exception.getMessage());
            redirectToLogin(response, exception.getMessage());
        }
    }

    private void redirectToLogin(HttpServletResponse response, String message) throws IOException {
        response.sendRedirect(properties.frontendUrl() + "/login?error="
                + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    private static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** PKCE S256: base64url(SHA-256(verifier)). */
    static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
