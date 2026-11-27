package hu.bplusn.portal.jira;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookieHeadersTest {

    @Test
    void buildsCookieHeaderFromSetCookie() {
        String header = CookieHeaders.fromSetCookie(List.of(
                "JSESSIONID=ABC123; Path=/; Secure; HttpOnly",
                "atlassian.xsrf.token=XYZ|lin; Path=/; Secure",
                "AWSALB=lb1; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/"));
        assertEquals("JSESSIONID=ABC123; atlassian.xsrf.token=XYZ|lin; AWSALB=lb1", header);
    }

    @Test
    void deletionCookieRemovesEarlierValue() {
        assertEquals("b=2", CookieHeaders.fromSetCookie(List.of("a=1", "b=2", "a=; Max-Age=0")));
    }

    @Test
    void emptyInput() {
        assertEquals("", CookieHeaders.fromSetCookie(null));
        assertEquals("", CookieHeaders.fromSetCookie(List.of()));
    }
}
