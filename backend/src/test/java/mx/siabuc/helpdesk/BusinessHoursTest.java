package mx.siabuc.helpdesk;

import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessHoursTest {
    private static final Set<Integer> WEEKDAYS=Set.of(1,2,3,4,5);
    private Instant add(String from,int minutes,String zone,Set<LocalDate> holidays) {
        return BusinessHours.addMinutes(Instant.parse(from),minutes,ZoneId.of(zone),WEEKDAYS,LocalTime.of(9,0),LocalTime.of(17,0),holidays);
    }
    @Test void skipsWeekendAndConfiguredHoliday() {
        assertThat(add("2026-10-09T22:30:00Z",120,"America/Mexico_City",Set.of(LocalDate.of(2026,10,12))))
            .isEqualTo(Instant.parse("2026-10-13T16:30:00Z"));
    }
    @Test void startsAtOpeningAfterAnOutOfHoursRequest() {
        assertThat(add("2026-10-10T18:00:00Z",120,"America/Mexico_City",Set.of()))
            .isEqualTo(Instant.parse("2026-10-12T17:00:00Z"));
    }
    @Test void exactCloseBoundaryIsValidAndFractionalSecondsArePreserved() {
        assertThat(add("2026-10-09T22:00:00Z",60,"America/Mexico_City",Set.of()))
            .isEqualTo(Instant.parse("2026-10-09T23:00:00Z"));
        assertThat(add("2026-10-09T22:59:30Z",1,"America/Mexico_City",Set.of()))
            .isEqualTo(Instant.parse("2026-10-12T15:00:30Z"));
    }
    @Test void observesTimezoneOffsetChangeAcrossWeekend() {
        assertThat(add("2026-03-06T21:30:00Z",120,"America/New_York",Set.of()))
            .isEqualTo(Instant.parse("2026-03-09T14:30:00Z"));
    }
    @Test void configuredSundayIsWorkingAndOtherDaysAreExcluded() {
        assertThat(BusinessHours.addMinutes(Instant.parse("2026-10-10T18:00:00Z"),60,ZoneId.of("UTC"),Set.of(7),LocalTime.of(10,0),LocalTime.of(12,0),Set.of()))
            .isEqualTo(Instant.parse("2026-10-11T11:00:00Z"));
    }
}
