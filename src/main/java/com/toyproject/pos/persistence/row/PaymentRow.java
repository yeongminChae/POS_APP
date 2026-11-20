package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * {@code PAYMENTS} 테이블 1행 — 결제 공통 정보 (수단·상태·청구액·승인 시각).
 * 저장하면 MyBatis 가 DB 생성키(PAYMENT_ID)를 이 객체에 채워 주고, 현금 상세({@link CashPaymentRow})가 그 값을 쓴다.
 */
@Getter
@Builder
@AllArgsConstructor
public class PaymentRow {
    private final Long paymentId;          // PK — DB 자동 생성 (저장 시 null, insert 후 useGeneratedKeys 로 채워짐)
    private final Long saleId;               // FK
    private final PaymentEnums.PaymentMethod method;             // "CASH","CARD",...
    private final PaymentEnums.PaymentStatus status;             // "APPROVED","REFUNDED"...
    private final BigDecimal amountDueToPay; // 수단별 청구(or 환불) 금액
    private final String approvedAt;         // 승인 시각 "yyyy-MM-dd HH:mm:ss" (Asia/Seoul)

    /** 결제 도메인 → 행 */
    public static PaymentRow of(Payment payment, Long saleId) {
        return PaymentRow.builder()
                .paymentId(null)
                .saleId(saleId)
                .method(payment.method())
                .status(payment.getPaymentStatus())
                .amountDueToPay(payment.amountDue())
                .approvedAt(payment.approvedAt())
                .build();
    }

}
