package com.toyproject.pos.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 현재 시각 포맷 공용 유틸 (타임존은 호출부에서 지정)
public final class DateFormatUtil {

    private static final String DATE_PATTERN = "yyyyMMdd";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private DateFormatUtil() {
    }

    // 현재 날짜 (yyyyMMdd)
    public static String nowDate(ZoneId zoneId) {
        return format(DATE_PATTERN, zoneId);
    }

    // 현재 일시 (yyyy-MM-dd HH:mm:ss)
    public static String nowDateTime(ZoneId zoneId) {
        return format(DATE_TIME_PATTERN, zoneId);
    }

    private static String format(String pattern, ZoneId zoneId) {
        return DateTimeFormatter.ofPattern(pattern)
                .withZone(zoneId)
                .format(Instant.now());
    }

}
