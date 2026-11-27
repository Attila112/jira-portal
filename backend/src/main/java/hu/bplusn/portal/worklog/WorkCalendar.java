package hu.bplusn.portal.worklog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Munkanap-naptár: hétfő–péntek, a beállított munkaszüneti napok nélkül,
 * az áthelyezett (szombati) munkanapokkal együtt, a felhasználó saját szabadságnapjai nélkül.
 */
public final class WorkCalendar {

    private final Set<LocalDate> holidays;
    private final Set<LocalDate> extraWorkdays;
    private final Set<LocalDate> leaves;

    public WorkCalendar(Collection<LocalDate> holidays, Collection<LocalDate> extraWorkdays) {
        this(holidays, extraWorkdays, Set.of());
    }

    public WorkCalendar(Collection<LocalDate> holidays, Collection<LocalDate> extraWorkdays,
                        Collection<LocalDate> leaves) {
        this.holidays = Set.copyOf(holidays);
        this.extraWorkdays = Set.copyOf(extraWorkdays);
        this.leaves = Set.copyOf(leaves);
    }

    public static WorkCalendar weekdaysOnly() {
        return new WorkCalendar(Set.of(), Set.of());
    }

    /** YYYY-MM-DD szövegekből (application.yml). */
    public static WorkCalendar fromStrings(Collection<String> holidays, Collection<String> extraWorkdays) {
        return new WorkCalendar(parse(holidays), parse(extraWorkdays));
    }

    /** Ugyanez a naptár egy felhasználó szabadságnapjaival. */
    public WorkCalendar withLeaves(Collection<LocalDate> leaveDays) {
        return new WorkCalendar(holidays, extraWorkdays, leaveDays);
    }

    private static Set<LocalDate> parse(Collection<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> LocalDate.parse(value.trim()))
                .collect(Collectors.toSet());
    }

    /** Munkanap lenne-e a szabadságtól függetlenül. */
    private boolean scheduledWorkday(LocalDate date) {
        if (extraWorkdays.contains(date)) {
            return true;
        }
        if (holidays.contains(date)) {
            return false;
        }
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** Ténylegesen ledolgozandó munkanap (szabadság nélkül). */
    public boolean isWorkday(LocalDate date) {
        return scheduledWorkday(date) && !leaves.contains(date);
    }

    /** Szabadság egy olyan napon, amely egyébként munkanap lenne (hétvégére eső szabadság nem számít). */
    public boolean isLeave(LocalDate date) {
        return leaves.contains(date) && scheduledWorkday(date);
    }

    /** Munkanapok száma a [from, toInclusive] intervallumban (üres intervallumnál 0). */
    public int workdays(LocalDate from, LocalDate toInclusive) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(toInclusive); date = date.plusDays(1)) {
            if (isWorkday(date)) {
                count++;
            }
        }
        return count;
    }

    /** Szabadságnapok száma a [from, toInclusive] intervallumban. */
    public int leaveDays(LocalDate from, LocalDate toInclusive) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(toInclusive); date = date.plusDays(1)) {
            if (isLeave(date)) {
                count++;
            }
        }
        return count;
    }
}
