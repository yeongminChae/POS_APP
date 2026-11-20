package com.toyproject.pos.domain.dto.payment;

import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 결제 (공통 + 수단별 페이로드)
/**
 * 결제 응답 — {@code SaleResDto.payment}. 수단과 무관한 공통 필드 + 수단별 상세(지금은 현금 {@code cash}뿐).
 * 카드 결제를 구현하면 {@code card} 같은 상세 필드를 같은 방식으로 추가한다.
 */
@Getter
@AllArgsConstructor
@Builder
public class PaymentResDto {

    private final PaymentEnums.PaymentMethod method;             // "CASH"
    private final PaymentEnums.PaymentStatus status;             // "APPROVED"
    private final BigDecimal amountDueToPay;         // 고객이 지불한 금액(=청구액)
    private final CashPayloadDto cash;          // 현금일 때만, 그 외 null
    private final String approvedAt;              // 승인 시각 "yyyy-MM-dd HH:mm:ss" (Asia/Seoul)

    /** 결제 도메인 → 응답. 현금 결제({@link CashPayment})일 때만 cash 를 채우고 그 외는 null. */
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
