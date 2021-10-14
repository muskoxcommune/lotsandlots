package io.lotsandlots.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class DateFormatter {

    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public static String epochSecondsToDateString(Long epochSeconds) {
        return epochSecondsToDateString(epochSeconds, DEFAULT_FORMATTER);
    }

    public static String epochSecondsToDateString(Long epochSeconds, String format) {
        return epochSecondsToDateString(epochSeconds, DateTimeFormatter.ofPattern(format));
    }

    public static String epochSecondsToDateString(Long epochSeconds, DateTimeFormatter formatter) {
        return formatter.format(LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC));
    }
}
