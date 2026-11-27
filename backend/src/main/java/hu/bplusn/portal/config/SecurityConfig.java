package hu.bplusn.portal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Biztonsági beállítások:
 * <ul>
 *   <li>szerveroldali munkamenet (HttpOnly, SameSite=Strict cookie), 8 óra lejárattal (application.yml);</li>
 *   <li>CSRF: XSRF-TOKEN cookie + X-XSRF-TOKEN fejléc minden módosító kérésnél;</li>
 *   <li>/api/** csak belépve, a statikus frontend fájlok szabadon;</li>
 *   <li>szigorú biztonsági fejlécek (CSP, frame tiltás, no-referrer).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    /**
     * A belépést az AuthController végzi a Jirán keresztül, Spring-féle felhasználókezelés nincs.
     * Ez a bean kikapcsolja a "Using generated security password" alapértelmezett felhasználót.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new BadCredentialsException("A belépés csak a /api/auth/login végponton lehetséges.");
        };
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                                   PortalProperties properties) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(properties.cookieSecure()));

        http
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/csrf", "/api/auth/options", "/api/auth/oauth/start",
                                "/login/oauth2/code/jira", "/api/health", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; connect-src 'self'; object-src 'none'; "
                                        + "base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** A CSRF-token betöltése minden kérésnél, hogy az XSRF-TOKEN cookie mindig meglegyen. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
