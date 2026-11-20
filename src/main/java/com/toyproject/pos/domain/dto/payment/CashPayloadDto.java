package com.toyproject.pos.domain.dto.payment;

import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 현금 결제 상세 응답 — {@code PaymentResDto.cash}. 현금 결제일 때만 만들어진다.
 */
@Getter
@AllArgsConstructor
@Builder
public class CashPayloadDto {

    private final BigDecimal amountReceived;  // 받은 금액 (반품은 음수)
    private final BigDecimal change;          // 거스름돈 = 받은 금액 − 청구액

    /** 결제 → 현금 상세. 거스름돈은 {@link CashPayment#calculateChange}로 계산한다 (받은 금액이 있는 결제만 넘겨야 함). */
    public static CashPayloadDto of(Payment payment) {
        return CashPayloadDto.builder()
                .amountReceived(payment.amountReceived())
                .change(CashPayment.calculateChange(payment.amountReceived(), payment.amountDue()))
                .build();
    }

}
