package com.toyproject.pos.domain.dto.payment;

import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class CashPayloadDto {

    private final BigDecimal amountReceived;
    private final BigDecimal change;

    public static CashPayloadDto of(Payment payment) {
        return CashPayloadDto.builder()
                .amountReceived(payment.amountReceived())
                .change(CashPayment.calculateChange(payment.amountReceived(), payment.amountDue()))
                .build();
    }

}
