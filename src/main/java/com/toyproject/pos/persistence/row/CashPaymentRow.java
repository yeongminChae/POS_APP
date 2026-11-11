package com.toyproject.pos.persistence.row;

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

    public static CashPaymentRow of(Payment payment) {

        return CashPaymentRow.builder()
                .paymentId(null)
                .amountReceived(payment.amountReceived())
                .change(getChangeCash(payment))
                .build();
    }

    private static BigDecimal getChangeCash(Payment payment) {
        if (payment.method().name().equals("CASH") == false) return null;

        return payment.amountReceived().subtract(payment.amountDue());
    }

}