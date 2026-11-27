package hu.bplusn.portal.jira;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * A Jira felé használt hitelesítés, a felhasználó szerveroldali munkamenetében tárolva.
 * <ul>
 *   <li><b>oauth</b>: "Belépés Jirával" – OAuth 2.0 access + refresh token (céges SSO a Jirán keresztül).</li>
 *   <li><b>session</b>: felhasználónév/jelszó belépéskor a Jira munkamenet-cookie-ja (a jelszót nem tároljuk).</li>
 *   <li><b>basic</b>: felhasználónév + jelszó a szerver memóriájában (tartalék).</li>
 * </ul>
 * A böngésző ezekből semmit nem kap meg, csak egy átlátszatlan munkamenet-azonosítót.
 * Az objektum megváltoztathatatlan: tokenfrissítéskor új példány készül.
 */
public final class JiraCredentials implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    public enum Mode { OAUTH, SESSION, BASIC }

    private final Mode mode;
    private final String username;
    private final String cookieHeader;
    private final String password;
    private final String accessToken;
    private final String refreshToken;
    private final Instant expiresAt;

    private JiraCredentials(Mode mode, String username, String cookieHeader, String password,
                            String accessToken, String refreshToken, Instant expiresAt) {
        this.mode = mode;
        this.username = username;
        this.cookieHeader = cookieHeader;
        this.password = password;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    public static JiraCredentials session(String username, String cookieHeader) {
        return new JiraCredentials(Mode.SESSION, username, cookieHeader, null, null, null, null);
    }

    public static JiraCredentials basic(String username, String password) {
        return new JiraCredentials(Mode.BASIC, username, null, password, null, null, null);
    }

    public static JiraCredentials oauth(String accessToken, String refreshToken, Instant expiresAt) {
        return new JiraCredentials(Mode.OAUTH, null, null, null, accessToken, refreshToken, expiresAt);
    }

    /** Ugyanaz az OAuth-belépés, a felhasználónévvel kiegészítve (a portalMe válasza után). */
    public JiraCredentials withUsername(String value) {
        return new JiraCredentials(mode, value, cookieHeader, password, accessToken, refreshToken, expiresAt);
    }

    public Mode mode() {
        return mode;
    }

    public String username() {
        return username;
    }

    public String cookieHeader() {
        return cookieHeader;
    }

    public String password() {
        return password;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isSession() {
        return mode == Mode.SESSION;
    }

    public boolean isOAuth() {
        return mode == Mode.OAUTH;
    }

    /** OAuth-token, amely a megadott időn belül lejár (vagy már lejárt). */
    public boolean expiresWithin(long seconds, Instant now) {
        return isOAuth() && expiresAt != null && !expiresAt.isAfter(now.plusSeconds(seconds));
    }

    @Override
    public String toString() {
        return "JiraCredentials[" + username + ", mode=" + mode + "]";
    }
}
