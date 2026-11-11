package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class CashPaymentRow {
    private final Long paymentId;          // PK=FK
    private final BigDecimal amountReceived;
    private final BigDecimal change;

    // paymentId: 같은 결제의 PAYMENTS 생성키 (PK = FK)
    public static CashPaymentRow of(Payment payment, Long paymentId) {

        return CashPaymentRow.builder()
                .paymentId(paymentId)
                .amountReceived(payment.amountReceived())
                .change(CashPayment.calculateChange(payment.amountReceived(), payment.amountDue()))
                .build();
    }

}