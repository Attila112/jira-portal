package hu.bplusn.portal.auth;

import java.io.Serializable;

/** A bejelentkezett felhasználó (Spring Security principal). */
public record PortalUser(String key, String username, String displayName, String email, boolean admin)
        implements Serializable {
}
