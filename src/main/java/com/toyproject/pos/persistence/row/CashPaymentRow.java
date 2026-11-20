package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * {@code CASH_PAYMENTS} 테이블 1행 — 현금 결제 상세 (받은 금액·거스름돈).
 * PK 가 곧 {@code PAYMENTS.PAYMENT_ID}(1:1)라서, 같은 결제의 PAYMENTS 를 먼저 저장해 생성키를 받은 뒤 저장한다.
 */
@Getter
@Builder
@AllArgsConstructor
public class CashPaymentRow {
    private final Long paymentId;          // PK=FK
    private final BigDecimal amountReceived; // 받은 금액 (반품은 음수)
    private final BigDecimal change;         // 거스름돈 = 받은 금액 − 청구액 (반품은 0)

    // paymentId: 같은 결제의 PAYMENTS 생성키 (PK = FK)
    /** 결제 도메인 → 행. 거스름돈은 {@link CashPayment#calculateChange}로 다시 계산한다. */
    public static CashPaymentRow of(Payment payment, Long paymentId) {

        return CashPaymentRow.builder()
                .paymentId(paymentId)
                .amountReceived(payment.amountReceived())
                .change(CashPayment.calculateChange(payment.amountReceived(), payment.amountDue()))
                .build();
    }

}
