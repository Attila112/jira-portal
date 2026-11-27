package hu.bplusn.portal.auth;

import hu.bplusn.portal.jira.JiraGateway.LoginResult;
import hu.bplusn.portal.jira.JiraModels.JiraUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Sikeres Jira-belépés után új, hitelesített portál-munkamenetet indít (jelszavas és OAuth belépéshez is). */
@Component
public class SessionStarter {

    private static final Logger log = LoggerFactory.getLogger(SessionStarter.class);

    private final SecurityContextRepository securityContextRepository;

    public SessionStarter(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public PortalUser start(LoginResult result, String method, HttpServletRequest request, HttpServletResponse response) {
        JiraUser jiraUser = result.me().user();
        PortalUser user = new PortalUser(jiraUser.key(), jiraUser.username(), jiraUser.displayName(),
                jiraUser.email(), result.me().admin());

        // Új munkamenet a session fixation ellen.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        JiraSession.store(session, result.credentials());

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (user.admin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.info("Belépés ({}): {} (admin={})", method, user.username(), user.admin());
        return user;
    }
}
