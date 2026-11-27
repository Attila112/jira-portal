package hu.bplusn.portal.jira;

/** A Jira-hívások hibái, a felhasználónak megjeleníthető magyar üzenettel. */
public class JiraException extends RuntimeException {

    public enum Kind {
        /** Hibás belépés vagy lejárt Jira-munkamenet: újra be kell jelentkezni. */
        UNAUTHORIZED(401),
        /** A Jira megtagadta a műveletet (jogosultság, CAPTCHA). */
        FORBIDDEN(403),
        /** Nem létező végpont vagy felhasználó. */
        NOT_FOUND(404),
        /** Hibás paraméter vagy JQL. */
        BAD_REQUEST(400),
        /** A Jira nem érhető el, időtúllépés, váratlan válasz. */
        UNAVAILABLE(503);

        private final int httpStatus;

        Kind(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    private final Kind kind;

    public JiraException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public JiraException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
