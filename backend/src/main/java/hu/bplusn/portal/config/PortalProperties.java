package hu.bplusn.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Az application.yml "portal" szekciója. */
@ConfigurationProperties(prefix = "portal")
public record PortalProperties(String dataDir, boolean cookieSecure, String frontendUrl, Boolean passwordLogin,
                               Database database, Jira jira, Worklog worklog) {

    public PortalProperties {
        dataDir = (dataDir == null || dataDir.isBlank()) ? "./data" : dataDir;
        // Belépés után ide irányítunk vissza (fejlesztéskor a Vite: http://localhost:5173, élesben üres = ugyanaz a host).
        frontendUrl = frontendUrl == null ? "" : frontendUrl.trim().replaceAll("/+$", "");
        passwordLogin = passwordLogin == null ? Boolean.TRUE : passwordLogin;
        database = database == null ? new Database(null, null, null) : database;
        jira = jira == null ? new Jira(null, null, null, null, null, null, null, null) : jira;
        worklog = worklog == null ? new Worklog(null, 0, null, null, null, null) : worklog;
    }

    /**
     * @param baseUrl        a Jira címe, csak HTTPS (pl. https://servicedesk.bnref.hu)
     * @param authMode       "session" (Jira munkamenet-cookie, jelszó nem marad meg) vagy "basic"
     * @param trustStoreType  pl. "Windows-ROOT": a Windows tanúsítványtárát használja (céges belső CA esetén);
     *                        üresen a JDK saját cacerts tára
     * @param trustStore      opcionális truststore fájl (JKS/PKCS12) útvonala
     */
    public record Jira(String baseUrl, String authMode, Duration connectTimeout, Duration readTimeout,
                       String trustStoreType, String trustStore, String trustStorePassword, OAuth oauth) {
        public Jira {
            oauth = oauth == null ? new OAuth(null, null, null, null, null) : oauth;
            baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
            authMode = (authMode == null || authMode.isBlank()) ? "session" : authMode.trim().toLowerCase();
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(180) : readTimeout;
        }
    }

    /**
     * Opcionális PostgreSQL adatbázis (pl. Neon) a szabadságokhoz és mentett személyekhez.
     * Üres url esetén helyi fájlokba ment (data mappa). Elfogad JDBC és "postgresql://user:pass@host/db" alakot is.
     */
    public record Database(String url, String username, String password) {
        public Database {
            url = url == null ? "" : url.trim();
        }
    }

    /**
     * "Belépés Jirával" (Jira Data Center OAuth 2.0, bejövő Application Link).
     *
     * @param clientId     az Application Link Client ID-ja (üres = kikapcsolva)
     * @param clientSecret az Application Link Client secret-je
     * @param redirectUri  pontosan az Application Linkben megadott Redirect URL
     * @param scope        READ (elég a ScriptRunner végpontokhoz)
     * @param pkce         PKCE (S256) küldése; alapból ki, mert a Jira token végpontja ezzel 500-at adhat
     */
    public record OAuth(String clientId, String clientSecret, String redirectUri, String scope, Boolean pkce) {
        public OAuth {
            clientId = clientId == null ? "" : clientId.trim();
            clientSecret = clientSecret == null ? "" : clientSecret.trim();
            redirectUri = (redirectUri == null || redirectUri.isBlank())
                    ? "http://localhost:8080/login/oauth2/code/jira" : redirectUri.trim();
            scope = (scope == null || scope.isBlank()) ? "READ" : scope.trim();
            pkce = pkce != null && pkce;
        }

        public boolean enabled() {
            return !clientId.isEmpty() && !clientSecret.isEmpty();
        }
    }

    /**
     * @param zone           a naptár időzónája
     * @param hoursPerDay    napi munkaidő (havi kerethez)
     * @param scope          "authored" (minden issue, amire logolt) vagy "assigned" (régi logika)
     * @param projectPresets a Worklog oldalon választható projektszűrők
     * @param holidays       munkaszüneti napok (YYYY-MM-DD), amelyek hétköznapra esnek
     * @param extraWorkdays  áthelyezett, szombati munkanapok (YYYY-MM-DD)
     */
    public record Worklog(String zone, double hoursPerDay, String scope, List<ProjectPreset> projectPresets,
                          List<String> holidays, List<String> extraWorkdays) {
        public Worklog {
            zone = (zone == null || zone.isBlank()) ? "Europe/Budapest" : zone;
            hoursPerDay = hoursPerDay <= 0 ? 8.0 : hoursPerDay;
            scope = (scope == null || scope.isBlank()) ? "authored" : scope;
            projectPresets = projectPresets == null || projectPresets.isEmpty()
                    ? List.of(new ProjectPreset("Összes projekt", ""))
                    : List.copyOf(projectPresets);
            holidays = holidays == null ? List.of() : List.copyOf(holidays);
            extraWorkdays = extraWorkdays == null ? List.of() : List.copyOf(extraWorkdays);
        }
    }

    public record ProjectPreset(String label, String projects) {
        public ProjectPreset {
            projects = projects == null ? "" : projects;
        }
    }
}
