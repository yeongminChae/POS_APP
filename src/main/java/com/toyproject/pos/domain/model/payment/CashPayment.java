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
                .change(calculateChange(amountReceived, commonPayment.getAmountDueToPay()))
                // 현금 거래일 경우 바로 승인 처리
                .status(PaymentEnums.PaymentStatus.APPROVED)
                .build();

    }

    // 거스름돈 = 받은 금액 - 결제할 금액 (Row/DTO 도 이 계산을 사용)
    public static BigDecimal calculateChange(BigDecimal amountReceived, BigDecimal amountDue) {
        return amountReceived.subtract(amountDue);
    }

    // 반품: 환불액만큼 음수로 돌려줌. 청구액도 음수라 거스름돈 = (-환불액) - (-청구액)
    public Payment refundOf(BigDecimal amountToRefund) {
        CommonPaymentData refundCommon = commonPayment.refundOf();
        BigDecimal refundReceived = amountToRefund.negate();

        return this.toBuilder()
                .commonPayment(refundCommon)
                .status(PaymentEnums.PaymentStatus.REFUNDED)
                .amountReceived(refundReceived)
                .change(calculateChange(refundReceived, refundCommon.getAmountDueToPay()))
                .build();
    }

}
