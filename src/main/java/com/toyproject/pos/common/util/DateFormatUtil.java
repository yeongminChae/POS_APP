package com.toyproject.pos.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 현재 시각 포맷 공용 유틸.
 * 영수증 번호·결제 승인 시각·반품 일자를 모두 이 클래스로 만들어, 서버의 기본 타임존 설정과 상관없이 점포 시간(한국)으로 기록한다.
 */
public final class DateFormatUtil {

    // 점포 기준 타임존. 서버 기본 타임존(ZoneId.systemDefault)과 무관하게 영수증·결제·반품 일시에 사용
    public static final ZoneId POS_ZONE = ZoneId.of("Asia/Seoul");

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
