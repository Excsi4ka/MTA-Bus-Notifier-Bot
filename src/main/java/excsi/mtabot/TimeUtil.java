package excsi.mtabot;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class TimeUtil {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private static final DateTimeFormatter USER_TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mma", Locale.US);

    public static long userTimeToSeconds(String timeStr) {
        try {
            LocalTime localTime = LocalTime.parse(timeStr, USER_TIME_FORMAT);
            LocalDate serviceDate = LocalDate.now(NY_ZONE);
            ZonedDateTime zonedDateTime = ZonedDateTime.of(serviceDate, localTime, NY_ZONE);
            return zonedDateTime.toEpochSecond();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format. Expected hh:mma (e.g. 08:30AM)", e);
        }
    }

    public static String timeToString(long seconds) {
        ZonedDateTime zonedDateTime = Instant.ofEpochSecond(seconds).atZone(NY_ZONE);
        return zonedDateTime.format(USER_TIME_FORMAT);
    }

    public static long nowSeconds() {
        return ZonedDateTime.now(NY_ZONE).toEpochSecond();
    }
}
