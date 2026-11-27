package hu.bplusn.portal.worklog;

import hu.bplusn.portal.storage.PortalStorage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A felhasználók saját szabadságnapjai, felhasználónként egy dokumentumban (fájl vagy PostgreSQL).
 * A szabadságnap nem számít munkanapnak: kiesik a havi keretből és a munkanapi átlag osztójából.
 */
@Component
public class LeaveStore {

    public static final int MAX_LEAVES = 1000;

    public enum LeaveType { VACATION, SICK, OTHER }

    /** Egy szabadságnap. */
    public record Leave(String date, LeaveType type, String note) {
    }

    private static final String COLLECTION = "leaves";

    private final PortalStorage storage;

    public LeaveStore(PortalStorage storage) {
        this.storage = storage;
    }

    public synchronized List<Leave> list(String username) {
        List<Leave> leaves = new ArrayList<>();
        for (String line : storage.read(COLLECTION, ownerId(username))) {
            String[] parts = line.split("\t", 3);
            if (parts.length >= 2 && !parts[0].isBlank()) {
                LeaveType type;
                try {
                    type = LeaveType.valueOf(parts[1]);
                } catch (IllegalArgumentException unknown) {
                    type = LeaveType.OTHER;
                }
                leaves.add(new Leave(parts[0], type, parts.length == 3 ? parts[2] : ""));
            }
        }
        leaves.sort(Comparator.comparing(Leave::date));
        return leaves;
    }

    public Set<LocalDate> dates(String username) {
        return list(username).stream().map(leave -> LocalDate.parse(leave.date())).collect(Collectors.toSet());
    }

    public synchronized List<Leave> save(String username, List<Leave> leaves) {
        if (leaves == null) {
            leaves = List.of();
        }
        if (leaves.size() > MAX_LEAVES) {
            throw new IllegalArgumentException("Legfeljebb " + MAX_LEAVES + " szabadságnap menthető.");
        }
        TreeMap<String, Leave> byDate = new TreeMap<>();
        for (Leave leave : leaves) {
            if (leave == null || leave.date() == null) {
                throw new IllegalArgumentException("Hiányzó dátum.");
            }
            LocalDate date;
            try {
                date = LocalDate.parse(leave.date().trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("Érvénytelen dátum: " + leave.date());
            }
            if (date.getYear() < 2000 || date.getYear() > 2100) {
                throw new IllegalArgumentException("Érvénytelen dátum: " + leave.date());
            }
            String note = leave.note() == null ? "" : leave.note().trim();
            if (note.length() > 200 || note.chars().anyMatch(ch -> ch == '\t' || ch == '\n' || ch == '\r')) {
                throw new IllegalArgumentException("Érvénytelen megjegyzés.");
            }
            LeaveType type = leave.type() == null ? LeaveType.VACATION : leave.type();
            byDate.put(date.toString(), new Leave(date.toString(), type, note));
        }

        List<String> lines = byDate.values().stream()
                .map(leave -> leave.date() + "\t" + leave.type().name() + "\t" + leave.note())
                .toList();
        storage.write(COLLECTION, ownerId(username), lines);
        return new ArrayList<>(byDate.values());
    }

    /** A felhasználónév SHA-256 lenyomata (a tárolóban nem jelenik meg az e-mail-cím). */
    private static String ownerId(String username) {
        try {
            String key = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
