package hu.bplusn.portal.auth;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Egyszerű belépési korlát (egy példányon, memóriában):
 * felhasználónként 5, összesen 60 kísérlet percenként. Így a Jira-fiók nem kerül könnyen CAPTCHA/zárolás alá.
 */
@Component
public class LoginRateLimiter {

    static final int PER_USER_LIMIT = 5;
    static final int GLOBAL_LIMIT = 60;
    private static final long WINDOW_NANOS = 60_000_000_000L;

    private record Attempt(long at, String user) {
    }

    private final Deque<Attempt> attempts = new ArrayDeque<>();

    /** true, ha a kísérlet engedélyezett (és rögzítve lett). */
    public synchronized boolean tryAcquire(String username) {
        long now = System.nanoTime();
        while (!attempts.isEmpty() && now - attempts.peekFirst().at() > WINDOW_NANOS) {
            attempts.pollFirst();
        }
        String user = username.toLowerCase(Locale.ROOT);
        long perUser = attempts.stream().filter(attempt -> attempt.user().equals(user)).count();
        if (attempts.size() >= GLOBAL_LIMIT || perUser >= PER_USER_LIMIT) {
            return false;
        }
        attempts.addLast(new Attempt(now, user));
        return true;
    }
}
