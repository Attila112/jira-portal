package hu.bplusn.portal.auth;

import hu.bplusn.portal.config.PortalProperties;
import hu.bplusn.portal.jira.JiraGateway;
import hu.bplusn.portal.jira.JiraGateway.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JiraGateway jira;
    private final LoginRateLimiter rateLimiter;
    private final SessionStarter sessionStarter;
    private final PortalProperties properties;

    public AuthController(JiraGateway jira, LoginRateLimiter rateLimiter, SessionStarter sessionStarter,
                          PortalProperties properties) {
        this.jira = jira;
        this.rateLimiter = rateLimiter;
        this.sessionStarter = sessionStarter;
        this.properties = properties;
    }

    public record LoginRequest(String username, String password) {
    }

    /** Amit a frontend a belépett felhasználóról és a beállításokról tud. */
    public record SessionInfo(PortalUser user, String jiraBaseUrl,
                              List<PortalProperties.ProjectPreset> projectPresets, double hoursPerDay) {
    }

    /** Milyen belépési módok érhetők el (a belépőoldal ez alapján jelenít meg gombokat). */
    public record AuthOptions(boolean jiraOAuth, boolean passwordLogin) {
    }

    /** A CSRF-cookie (XSRF-TOKEN) beállításához; a belépés előtt hívja a frontend. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public AuthOptions options() {
        return new AuthOptions(jira.oauthEnabled(), properties.passwordLogin());
    }

    @PostMapping("/login")
    public SessionInfo login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (!properties.passwordLogin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A jelszavas belépés ki van kapcsolva. Használd a Belépés Jirával gombot.");
        }
        String username = body == null || body.username() == null ? "" : body.username().trim();
        String password = body == null || body.password() == null ? "" : body.password();
        if (username.isEmpty() || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add meg a Jira-felhasználónevedet és jelszavadat.");
        }
        if (username.length() > 254 || password.length() > 4096) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Túl hosszú belépési adat.");
        }
        if (!rateLimiter.tryAcquire(username)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Túl sok belépési kísérlet. Várj egy percet.");
        }

        LoginResult result = jira.login(username, password);
        return sessionInfo(sessionStarter.start(result, "jelszó", request, response));
    }

    @GetMapping("/me")
    public SessionInfo me(@AuthenticationPrincipal PortalUser user) {
        return sessionInfo(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            jira.logout(JiraSession.find(session));
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private SessionInfo sessionInfo(PortalUser user) {
        return new SessionInfo(user, jira.baseUrl(), properties.worklog().projectPresets(),
                properties.worklog().hoursPerDay());
    }
}
