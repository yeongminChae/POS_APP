package com.toyproject.pos.domain.model.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
@Builder
public class RefundDetail {
    private final Long origSaleId;            // 원거래 SALE_ID
    private final String origReceiptNo;       // 선택 (조회 편의)
    private final String refundedAt;         // 환불 시각(서버 기준)
    private final String reason;              // 선택

    public static RefundDetail of(Sale sale) {
        return RefundDetail.builder()
                .origSaleId(sale.getSaleId())
                .origReceiptNo(sale.getReceiptNo())
                .refundedAt(getDateFormat())
                .reason("개인사유")
                .build();
    }

    private static String getDateFormat() {
        return DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
                ;
    }

}
