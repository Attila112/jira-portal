package hu.bplusn.portal.jira;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Set-Cookie fejlécekből a következő kérésekhez használható Cookie fejlécet állítja elő. */
public final class CookieHeaders {

    private CookieHeaders() {
    }

    public static String fromSetCookie(List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return "";
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String header : setCookieHeaders) {
            if (header == null) {
                continue;
            }
            String pair = header.split(";", 2)[0].trim();
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = pair.substring(0, separator).trim();
            String value = pair.substring(separator + 1).trim();
            if (value.isEmpty() || "\"\"".equals(value)) {
                cookies.remove(name); // törlő cookie
            } else {
                cookies.put(name, value);
            }
        }
        return cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }
}
