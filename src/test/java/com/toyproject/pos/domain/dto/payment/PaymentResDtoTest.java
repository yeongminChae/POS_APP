package com.toyproject.pos.domain.dto.payment;

import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.CommonPaymentData;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 응답의 수단별 페이로드 (A7). 카드 결제는 아직 400 으로 거절되므로 가짜 Payment 구현으로 확인한다.
 */
class PaymentResDtoTest {

    private static final CartKey KEY = CartKey.of("S01", "01", "9001");

    @Test
    @DisplayName("현금 결제는 cash 에 받은 금액·거스름돈을 담는다")
    void cashPayment_hasCashPayload() {
        Payment cash = CashPayment.restoredOf(info(PaymentEnums.PaymentMethod.CASH),
                new BigDecimal("2000"), new BigDecimal("500"), PaymentEnums.PaymentStatus.APPROVED);

        PaymentResDto dto = PaymentResDto.of(cash);

        assertThat(dto.getCash()).isNotNull();
        assertThat(dto.getCash().getAmountReceived()).isEqualByComparingTo("2000");
        assertThat(dto.getCash().getChange()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("현금이 아닌 결제는 cash = null")
    void nonCashPayment_hasNoCashPayload() {
        CommonPaymentData info = info(PaymentEnums.PaymentMethod.CARD);
        Payment card = new Payment() {
            @Override public CommonPaymentData info() { return info; }
            @Override public BigDecimal amountReceived() { return null; }
            @Override public PaymentEnums.PaymentStatus getPaymentStatus() { return PaymentEnums.PaymentStatus.APPROVED; }
        };

        PaymentResDto dto = PaymentResDto.of(card);

        assertThat(dto.getCash()).isNull();
        assertThat(dto.getMethod()).isEqualTo(PaymentEnums.PaymentMethod.CARD);
        assertThat(dto.getAmountDueToPay()).isEqualByComparingTo("1500");
    }

    private static CommonPaymentData info(PaymentEnums.PaymentMethod method) {
        return CommonPaymentData.restoredOf(1L, KEY, new BigDecimal("1500"), method, "2026-11-16 10:00:00");
    }
}
