package com.toyproject.pos.domain.dto.payment;

import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 결제 (공통 + 수단별 페이로드)
@Getter
@AllArgsConstructor
@Builder
public class PaymentResDto {

    private final PaymentEnums.PaymentMethod method;             // "CASH"
    private final PaymentEnums.PaymentStatus status;             // "APPROVED"
    private final BigDecimal amountDueToPay;         // 고객이 지불한 금액(=청구액)
    private final CashPayloadDto cash;          // 현금일 때만, 그 외 null
    private final String approvedAt;

    public static PaymentResDto of(Payment payment) {

        return PaymentResDto.builder()
                .method(payment.method())
                .status(payment.getPaymentStatus())
                .amountDueToPay(payment.amountDue())
                .cash(payment instanceof CashPayment ? CashPayloadDto.of(payment) : null)
                .approvedAt(payment.approvedAt())
                .build();
    }

}
