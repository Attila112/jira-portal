package hu.bplusn.portal.jira;

import hu.bplusn.portal.config.PortalProperties;
import hu.bplusn.portal.jira.JiraException.Kind;
import hu.bplusn.portal.jira.JiraModels.AuthSession;
import hu.bplusn.portal.jira.JiraModels.ErrorBody;
import hu.bplusn.portal.jira.JiraModels.IssuesResponse;
import hu.bplusn.portal.jira.JiraModels.MeResponse;
import hu.bplusn.portal.jira.JiraModels.WorklogsResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Valódi Jira Data Center kliens: a ScriptRunner REST végpontokat hívja a felhasználó nevében. */
@Component
@Profile("!mock")
public class HttpJiraGateway implements JiraGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpJiraGateway.class);
    private static final String ENDPOINT_PREFIX = "/rest/scriptrunner/latest/custom/";

    private final RestClient client;
    private final String baseUrl;
    private final String authMode;
    private final String worklogScope;
    private final PortalProperties.OAuth oauth;

    public HttpJiraGateway(PortalProperties properties) {
        PortalProperties.Jira jira = properties.jira();
        this.baseUrl = validateBaseUrl(jira.baseUrl());
        this.authMode = jira.authMode();
        this.worklogScope = properties.worklog().scope();
        this.oauth = jira.oauth();
        if (!authMode.equals("session") && !authMode.equals("basic")) {
            throw new IllegalStateException("portal.jira.auth-mode értéke 'session' vagy 'basic' lehet.");
        }

        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(jira.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.getDefault());
        SSLContext sslContext = buildSslContext(jira);
        if (sslContext != null) {
            httpClientBuilder.sslContext(sslContext);
        }
        HttpClient httpClient = httpClientBuilder.build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(jira.readTimeout());

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("Jira gateway: {} (auth-mode={}, Belépés Jirával: {})", baseUrl, authMode,
                oauth.enabled() ? "bekapcsolva, redirect=" + oauth.redirectUri() : "kikapcsolva");
    }

    /**
     * Egyedi tanúsítványtár a Jira HTTPS-kapcsolathoz. Céges belső CA esetén a JDK saját tára nem ismeri
     * a Jira tanúsítványát (PKIX hiba); Windows-on a "Windows-ROOT" típus a rendszer tanúsítványtárát használja.
     */
    private static SSLContext buildSslContext(PortalProperties.Jira jira) {
        String type = jira.trustStoreType() == null ? "" : jira.trustStoreType().trim();
        String path = jira.trustStore() == null ? "" : jira.trustStore().trim();
        if (type.isEmpty() && path.isEmpty()) {
            return null;
        }
        try {
            KeyStore keyStore = KeyStore.getInstance(type.isEmpty() ? KeyStore.getDefaultType() : type);
            if (path.isEmpty()) {
                keyStore.load(null, null);
            } else {
                char[] password = jira.trustStorePassword() == null || jira.trustStorePassword().isEmpty()
                        ? null : jira.trustStorePassword().toCharArray();
                try (InputStream input = Files.newInputStream(Path.of(path))) {
                    keyStore.load(input, password);
                }
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            log.info("Jira truststore: {}{}", type.isEmpty() ? KeyStore.getDefaultType() : type,
                    path.isEmpty() ? "" : " (" + path + ")");
            return context;
        } catch (Exception exception) {
            throw new IllegalStateException("A Jira truststore nem tölthető be (type=" + type + ", path=" + path + ")",
                    exception);
        }
    }

    private static String validateBaseUrl(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Érvénytelen portal.jira.base-url: " + value, exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException(
                    "A portal.jira.base-url csak HTTPS cím lehet, felhasználónév, query és fragment nélkül: " + value);
        }
        return value;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    /* ------------------------------------------------------------------ */
    /* Belépés / kilépés                                                   */
    /* ------------------------------------------------------------------ */

    @Override
    public LoginResult login(String username, String password) {
        JiraCredentials credentials = "basic".equals(authMode)
                ? JiraCredentials.basic(username, password)
                : createJiraSession(username, password);
        MeResponse me;
        try {
            me = get(credentials, "portalMe", Map.of(), MeResponse.class);
        } catch (JiraException exception) {
            if (exception.kind() == Kind.UNAUTHORIZED) {
                throw new JiraException(Kind.UNAUTHORIZED, "Hibás Jira-felhasználónév vagy jelszó.");
            }
            throw exception;
        }
        if (me.user() == null || me.user().key() == null || me.user().username() == null) {
            throw new JiraException(Kind.UNAVAILABLE, "A portalMe végpont nem adott vissza felhasználót.");
        }
        if (Boolean.FALSE.equals(me.user().active())) {
            throw new JiraException(Kind.FORBIDDEN, "A Jira-felhasználó inaktív.");
        }
        return new LoginResult(credentials, me);
    }

    private JiraCredentials createJiraSession(String username, String password) {
        return call(() -> client.post()
                .uri("/rest/auth/1/session")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Atlassian-Token", "no-check")
                .body(Map.of("username", username, "password", password))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 401) {
                        throw new JiraException(Kind.UNAUTHORIZED, "Hibás Jira-felhasználónév vagy jelszó.");
                    }
                    if (status == 403) {
                        throw new JiraException(Kind.FORBIDDEN,
                                "A Jira megtagadta a belépést (például CAPTCHA a sok hibás próbálkozás miatt). "
                                        + "Jelentkezz be egyszer a Jira webes felületén, majd próbáld újra.");
                    }
                    if (status >= 300 && status < 400) {
                        throw new JiraException(Kind.UNAVAILABLE,
                                "A Jira átirányítást küldött. Ellenőrizd a portal.jira.base-url beállítást.");
                    }
                    if (status != 200) {
                        throw new JiraException(Kind.UNAVAILABLE, "A Jira-belépés nem sikerült (HTTP " + status + ").");
                    }
                    String cookieHeader = CookieHeaders.fromSetCookie(response.getHeaders().get(HttpHeaders.SET_COOKIE));
                    if (cookieHeader.isBlank()) {
                        AuthSession body = response.bodyTo(AuthSession.class);
                        if (body == null || body.session() == null || body.session().value() == null) {
                            throw new JiraException(Kind.UNAVAILABLE, "A Jira nem adott vissza munkamenetet.");
                        }
                        cookieHeader = body.session().name() + "=" + body.session().value();
                    }
                    return JiraCredentials.session(username, cookieHeader);
                }));
    }

    /* ------------------------------------------------------------------ */
    /* Belépés Jirával (OAuth 2.0, authorization code + PKCE)              */
    /* ------------------------------------------------------------------ */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("refresh_token") String refreshToken,
                         @JsonProperty("expires_in") Long expiresIn) {
    }

    @Override
    public boolean oauthEnabled() {
        return oauth.enabled();
    }

    @Override
    public String oauthAuthorizeUrl(String state, String codeChallenge) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", oauth.clientId());
        query.put("redirect_uri", oauth.redirectUri());
        query.put("response_type", "code");
        query.put("scope", oauth.scope());
        query.put("state", state);
        if (oauth.pkce()) {
            query.put("code_challenge", codeChallenge);
            query.put("code_challenge_method", "S256");
        }
        StringBuilder url = new StringBuilder(baseUrl).append("/rest/oauth2/latest/authorize");
        char separator = '?';
        for (Map.Entry<String, String> entry : query.entrySet()) {
            url.append(separator).append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            separator = '&';
        }
        return url.toString();
    }

    @Override
    public LoginResult oauthLogin(String code, String codeVerifier) {
        requireOAuth();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", oauth.clientId());
        params.put("client_secret", oauth.clientSecret());
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        params.put("redirect_uri", oauth.redirectUri());
        if (oauth.pkce()) {
            params.put("code_verifier", codeVerifier);
        }
        JiraCredentials credentials = tokenRequest(params, null);

        MeResponse me = get(credentials, "portalMe", Map.of(), MeResponse.class);
        if (me.user() == null || me.user().key() == null || me.user().username() == null) {
            throw new JiraException(Kind.UNAVAILABLE, "A portalMe végpont nem adott vissza felhasználót.");
        }
        if (Boolean.FALSE.equals(me.user().active())) {
            throw new JiraException(Kind.FORBIDDEN, "A Jira-felhasználó inaktív.");
        }
        return new LoginResult(credentials.withUsername(me.user().username()), me);
    }

    @Override
    public JiraCredentials refreshIfNeeded(JiraCredentials credentials) {
        if (credentials == null || !credentials.expiresWithin(60, Instant.now())) {
            return credentials;
        }
        if (credentials.refreshToken() == null || credentials.refreshToken().isBlank()) {
            throw new JiraException(Kind.UNAUTHORIZED, "A Jira-belépés lejárt. Jelentkezz be újra.");
        }
        requireOAuth();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", oauth.clientId());
        params.put("client_secret", oauth.clientSecret());
        params.put("refresh_token", credentials.refreshToken());
        params.put("grant_type", "refresh_token");
        params.put("redirect_uri", oauth.redirectUri());
        JiraCredentials refreshed = tokenRequest(params, credentials.refreshToken());
        log.debug("OAuth token frissítve: {}", credentials.username());
        return refreshed.withUsername(credentials.username());
    }

    private void requireOAuth() {
        if (!oauth.enabled()) {
            throw new JiraException(Kind.BAD_REQUEST, "A Belépés Jirával nincs beállítva (portal.jira.oauth).");
        }
    }

    /**
     * Token végpont hívása. Pontosan úgy, ahogy a működő PowerShell-teszt: a paraméterek a query-ben,
     * a Content-Type pedig application/x-www-form-urlencoded (a Jira @FormParam ezt követeli meg), üres törzzsel.
     */
    private JiraCredentials tokenRequest(Map<String, String> params, String previousRefreshToken) {
        log.info("Jira OAuth token kérés (grant_type={}, query + form content-type)", params.get("grant_type"));
        TokenResponse token = call(() -> client.post()
                .uri(builder -> {
                    builder.path("/rest/oauth2/latest/token");
                    params.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
                    return builder.build(params);
                })
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header("X-Atlassian-Token", "no-check")
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 200) {
                        return response.bodyTo(TokenResponse.class);
                    }
                    String body;
                    try {
                        body = response.bodyTo(String.class);
                    } catch (RuntimeException unreadable) {
                        body = null;
                    }
                    log.warn("Jira token végpont: HTTP {} – {}", status,
                            body == null ? "(üres válasz)" : body.substring(0, Math.min(body.length(), 1000)));
                    if (status == 400 || status == 401) {
                        throw new JiraException(Kind.UNAUTHORIZED,
                                "A Jira nem fogadta el a belépést (lejárt vagy érvénytelen kód/token). Jelentkezz be újra.");
                    }
                    throw new JiraException(Kind.UNAVAILABLE, "A Jira token végpontja hibát adott (HTTP " + status + ").");
                }));
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new JiraException(Kind.UNAVAILABLE, "A Jira nem adott vissza access tokent.");
        }
        long lifetime = token.expiresIn() == null || token.expiresIn() <= 0 ? 3600 : token.expiresIn();
        String refreshToken = token.refreshToken() != null && !token.refreshToken().isBlank()
                ? token.refreshToken() : previousRefreshToken;
        return JiraCredentials.oauth(token.accessToken(), refreshToken, Instant.now().plusSeconds(lifetime));
    }

    @Override
    public void logout(JiraCredentials credentials) {
        if (credentials == null || !credentials.isSession()) {
            return;
        }
        try {
            client.delete()
                    .uri("/rest/auth/1/session")
                    .headers(headers -> applyAuth(credentials, headers))
                    .header("X-Atlassian-Token", "no-check")
                    .exchange((request, response) -> null);
        } catch (RuntimeException exception) {
            log.debug("Jira-munkamenet lezárása nem sikerült: {}", exception.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Adatlekérések                                                       */
    /* ------------------------------------------------------------------ */

    @Override
    public IssuesResponse myIssues(JiraCredentials credentials) {
        return get(credentials, "portalMyIssues", Map.of(), IssuesResponse.class);
    }

    @Override
    public WorklogsResponse worklogs(JiraCredentials credentials, String targetUser, int days, List<String> projects) {
        Map<String, String> query = new LinkedHashMap<>();
        if (targetUser != null && !targetUser.isBlank()) {
            query.put("user", targetUser.trim());
        }
        query.put("days", Integer.toString(days));
        if (projects != null && !projects.isEmpty()) {
            query.put("projects", String.join(",", projects));
        }
        query.put("scope", worklogScope);
        return get(credentials, "portalWorklogs", query, WorklogsResponse.class);
    }

    /* ------------------------------------------------------------------ */
    /* HTTP segédfüggvények                                                */
    /* ------------------------------------------------------------------ */

    private <T> T get(JiraCredentials credentials, String endpoint, Map<String, String> query, Class<T> type) {
        return call(() -> client.get()
                .uri(builder -> {
                    builder.path(ENDPOINT_PREFIX + endpoint);
                    // Változóként adjuk át az értékeket, így a '+', '@' stb. is helyesen kódolódik.
                    query.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
                    return builder.build(query);
                })
                .headers(headers -> applyAuth(credentials, headers))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status >= 200 && status < 300) {
                        T body = response.bodyTo(type);
                        if (body == null) {
                            throw new JiraException(Kind.UNAVAILABLE, "A Jira üres választ adott (" + endpoint + ").");
                        }
                        return body;
                    }
                    String message;
                    try {
                        message = errorMessage(response.bodyTo(ErrorBody.class));
                    } catch (RuntimeException notJson) {
                        message = null; // nem JSON (pl. Jira HTML hibaoldal)
                    }
                    throw switch (status) {
                        case 401 -> new JiraException(Kind.UNAUTHORIZED, "A Jira-munkamenet lejárt. Jelentkezz be újra.");
                        case 403 -> new JiraException(Kind.FORBIDDEN,
                                message != null ? message : "Ehhez nincs jogosultságod a Jirában.");
                        case 404 -> new JiraException(Kind.NOT_FOUND, message != null ? message
                                : "A ScriptRunner végpont nem található: " + endpoint
                                + ". Telepítsd a jira-scripts/portalEndpoints.groovy fájlt.");
                        case 400 -> new JiraException(Kind.BAD_REQUEST,
                                message != null ? message : "A Jira hibás kérést jelzett.");
                        default -> new JiraException(Kind.UNAVAILABLE, status >= 300 && status < 400
                                ? "A Jira átirányítást küldött. Ellenőrizd a portal.jira.base-url beállítást."
                                : "A Jira hibát jelzett (HTTP " + status + ")" + (message != null ? ": " + message : "."));
                    };
                }));
    }

    private static String errorMessage(ErrorBody body) {
        return body != null && body.error() != null && !body.error().isBlank() ? body.error() : null;
    }

    private static void applyAuth(JiraCredentials credentials, HttpHeaders headers) {
        if (credentials == null) {
            throw new JiraException(Kind.UNAUTHORIZED, "Nincs Jira-munkamenet. Jelentkezz be újra.");
        }
        if (credentials.isOAuth()) {
            headers.setBearerAuth(credentials.accessToken());
        } else if (credentials.isSession()) {
            headers.set(HttpHeaders.COOKIE, credentials.cookieHeader());
        } else {
            headers.setBasicAuth(credentials.username(), credentials.password(), StandardCharsets.UTF_8);
        }
    }

    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (JiraException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SSLHandshakeException.class)) {
                throw new JiraException(Kind.UNAVAILABLE,
                        "A Jira HTTPS-tanúsítványát a Java nem fogadja el. Windows-on indítsd "
                                + "JIRA_TRUST_STORE_TYPE=Windows-ROOT beállítással (lásd README).", exception);
            }
            String reason = exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timed out")
                    ? "A Jira lekérés túllépte az időkorlátot. Próbáld rövidebb időszakkal."
                    : "A Jira jelenleg nem érhető el a szerverről.";
            throw new JiraException(Kind.UNAVAILABLE, reason, exception);
        } catch (RestClientException exception) {
            throw new JiraException(Kind.UNAVAILABLE, "A Jira válasza nem dolgozható fel.", exception);
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
