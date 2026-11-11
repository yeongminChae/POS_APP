package com.toyproject.pos.domain.model.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class CashPayment implements Payment {

    private final CommonPaymentData commonPayment;
    private final BigDecimal amountReceived; // 받은 금액
    private final BigDecimal change;       // 거스름돈
    private final PaymentEnums.PaymentStatus status;

    @Override
    public CommonPaymentData info() {
        return commonPayment;
    }

    @Override
    public BigDecimal amountReceived() {
        return amountReceived;
    }

    @Override
    public PaymentEnums.PaymentStatus getPaymentStatus() {
        return status;
    }

    @Override
    public Payment refund(BigDecimal amountToRefund) {
        return refundOf(amountToRefund);
    }

    public static Payment of(
            CommonPaymentData commonPayment,
            BigDecimal amountReceived
    ) {

        if (amountReceived.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("지불 금액이 이상 합니다. 확인 부탁 드립니다.");

        return CashPayment.builder()
                .commonPayment(commonPayment)
                .amountReceived(amountReceived)
                .change(amountReceived.subtract(commonPayment.getAmountDueToPay()))
                // 현금 거래일 경우 바로 승인 처리
                .status(PaymentEnums.PaymentStatus.APPROVED)
                .build();

    }

    public Payment refundOf(BigDecimal amountReceived) {
        return this.toBuilder()
                .status(PaymentEnums.PaymentStatus.REFUNDED)
                .amountReceived(amountReceived.negate()) // negate 음수로 바꿔줌
                .change(BigDecimal.ZERO)
                .build();
    }

}
