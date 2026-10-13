package mx.siabuc.helpdesk;

import java.time.*;
import java.util.Set;

/** Adds elapsed working time using local calendar boundaries, including timezone offset changes. */
public final class BusinessHours {
    private BusinessHours() {}
    public static Instant addMinutes(Instant start, long minutes, ZoneId zone, Set<Integer> days,
            LocalTime opening, LocalTime closing, Set<LocalDate> holidays) {
        if (minutes < 0 || days.isEmpty() || !opening.isBefore(closing)) throw new IllegalArgumentException("Invalid business calendar");
        if (minutes == 0) return start;
        Duration remaining = Duration.ofMinutes(minutes);
        LocalDate date = start.atZone(zone).toLocalDate();
        for (int checked=0; checked<100_000; checked++, date=date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek().getValue()) || holidays.contains(date)) continue;
            Instant begin = date.atTime(opening).atZone(zone).toInstant();
            Instant end = date.atTime(closing).atZone(zone).toInstant();
            Instant cursor = start.isAfter(begin) ? start : begin;
            if (!cursor.isBefore(end)) continue;
            Duration available = Duration.between(cursor,end);
            if (remaining.compareTo(available)<=0) return cursor.plus(remaining);
            remaining=remaining.minus(available);
        }
        throw new IllegalArgumentException("Business calendar exceeds supported date range");
    }
}
