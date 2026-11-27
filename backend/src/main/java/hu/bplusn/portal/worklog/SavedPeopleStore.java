package hu.bplusn.portal.worklog;

import hu.bplusn.portal.storage.PortalStorage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Az adminok mentett személylistája, felhasználónként egy dokumentumban (fájl vagy PostgreSQL).
 */
@Component
public class SavedPeopleStore {

    public static final int MAX_PEOPLE = 200;

    public record SavedPerson(String username, String displayName) {
    }

    private static final String COLLECTION = "saved-people";

    private final PortalStorage storage;

    public SavedPeopleStore(PortalStorage storage) {
        this.storage = storage;
    }

    public synchronized List<SavedPerson> list(String ownerKey) {
        List<SavedPerson> people = new ArrayList<>();
        for (String line : storage.read(COLLECTION, ownerId(ownerKey))) {
            String[] parts = line.split("\t", 2);
            if (parts.length == 2 && !parts[0].isBlank()) {
                people.add(new SavedPerson(parts[0], parts[1]));
            }
        }
        return people;
    }

    public synchronized List<SavedPerson> save(String ownerKey, List<SavedPerson> people) {
        if (people == null) {
            people = List.of();
        }
        if (people.size() > MAX_PEOPLE) {
            throw new IllegalArgumentException("Legfeljebb " + MAX_PEOPLE + " személy menthető.");
        }
        Map<String, SavedPerson> unique = new LinkedHashMap<>();
        for (SavedPerson person : people) {
            String username = clean(person == null ? null : person.username(), "felhasználónév");
            String displayName = clean(person.displayName(), "megjelenített név");
            unique.put(username.toLowerCase(Locale.ROOT), new SavedPerson(username, displayName));
        }
        Collator collator = Collator.getInstance(Locale.forLanguageTag("hu-HU"));
        List<SavedPerson> sorted = new ArrayList<>(unique.values());
        sorted.sort((a, b) -> collator.compare(a.displayName(), b.displayName()));

        storage.write(COLLECTION, ownerId(ownerKey),
                sorted.stream().map(p -> p.username() + "\t" + p.displayName()).toList());
        return sorted;
    }

    private static String clean(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 200 || trimmed.chars().anyMatch(ch -> ch == '\t' || ch == '\n' || ch == '\r')) {
            throw new IllegalArgumentException("Érvénytelen " + field + ".");
        }
        return trimmed;
    }

    private static String ownerId(String ownerKey) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(ownerKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
