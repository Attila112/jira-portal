package hu.bplusn.portal.jira;

import hu.bplusn.portal.jira.JiraModels.IssuesResponse;
import hu.bplusn.portal.jira.JiraModels.MeResponse;
import hu.bplusn.portal.jira.JiraModels.WorklogsResponse;

import java.util.List;

/**
 * A Jira Data Center elérése. Két megvalósítás van:
 * {@link HttpJiraGateway} (valódi Jira, ScriptRunner végpontokon át) és
 * {@link MockJiraGateway} ("mock" profil, szimulált adatokkal, Jira nélkül).
 */
public interface JiraGateway {

    /** Belépés a Jirába felhasználónévvel és jelszóval; hibánál {@link JiraException}. */
    LoginResult login(String username, String password);

    /** Be van-e állítva a "Belépés Jirával" (OAuth 2.0). */
    boolean oauthEnabled();

    /** A Jira engedélyező oldalának címe (ide irányítjuk a böngészőt). */
    String oauthAuthorizeUrl(String state, String codeChallenge);

    /** Az OAuth visszahívás kódjának beváltása tokenre, majd a felhasználó lekérdezése. */
    LoginResult oauthLogin(String code, String codeVerifier);

    /** OAuth-token frissítése, ha hamarosan lejár; egyébként ugyanazt adja vissza. */
    JiraCredentials refreshIfNeeded(JiraCredentials credentials);

    /** Jira-munkamenet lezárása (best effort, hibát nem dob). */
    void logout(JiraCredentials credentials);

    /** A dashboard saját jegyei (portalMyIssues). */
    IssuesResponse myIssues(JiraCredentials credentials);

    /**
     * Worklog sorok (portalWorklogs).
     *
     * @param targetUser null vagy üres esetén a bejelentkezett felhasználó
     * @param projects   üres lista = minden projekt
     */
    WorklogsResponse worklogs(JiraCredentials credentials, String targetUser, int days, List<String> projects);

    /** A Jira alap URL-je (pl. jegylinkekhez). */
    String baseUrl();

    record LoginResult(JiraCredentials credentials, MeResponse me) {
    }
}
