package com.toyproject.pos.domain.model.sale;

import com.toyproject.pos.domain.model.cart.CartSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영수증 번호·반품 일자는 서버 기본 타임존과 무관하게 점포 기준(Asia/Seoul) 날짜여야 한다.
 *
 * <p>항상 날짜가 서울과 달라지는 타임존을 골라 JVM 기본값으로 설정한다.
 * 서울이 21시 전이면 UTC-12(21시간 늦음 → 항상 전날), 21시 이후면 UTC+14(5시간 빠름 → 항상 다음 날).</p>
 */
class SaleTimeZoneTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private TimeZone originalDefault;

    @BeforeEach
    void setDefaultZoneToDifferentDate() {
        originalDefault = TimeZone.getDefault();
        int seoulHour = ZonedDateTime.now(SEOUL).getHour();
        TimeZone.setDefault(TimeZone.getTimeZone(seoulHour < 21 ? "Etc/GMT+12" : "Etc/GMT-14"));
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefault);
    }

    @Test
    @DisplayName("영수증 번호의 날짜는 점포(서울) 기준")
    void receiptNo_usesSeoulDate() {
        String before = seoulToday();
        Sale sale = Sale.of(summary(), null);
        String after = seoulToday();

        String receiptDate = sale.getReceiptNo().split("-")[1]; // S01-yyyyMMdd-01-T1
        assertThat(receiptDate).isIn(before, after);
    }

    @Test
    @DisplayName("반품 일자는 점포(서울) 기준")
    void refundedAt_usesSeoulDate() {
        Sale original = Sale.of(summary(), null);

        String before = seoulToday();
        RefundDetail detail = RefundDetail.of(original, null);
        String after = seoulToday();

        assertThat(detail.getRefundedAt()).isIn(before, after);
    }

    private static String seoulToday() {
        return LocalDate.now(SEOUL).format(YYYYMMDD);
    }

    private static CartSummary summary() {
        return CartSummary.builder()
                .strCd("S01").posNo("01").tranNo("T1")
                .totalQty(1).totalAmount(new BigDecimal("1500"))
                .lines(List.of())
                .build();
    }
}
