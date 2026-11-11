package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class PaymentRow {
    private final Long paymentId;          // 직접 생성(UUID 등)
    private final Long saleId;               // FK
    private final PaymentEnums.PaymentMethod method;             // "CASH","CARD",...
    private final PaymentEnums.PaymentStatus status;             // "APPROVED","REFUNDED"...
    private final BigDecimal amountDueToPay; // 수단별 청구(or 환불) 금액
    private final String approvedAt;         // ISO8601

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

