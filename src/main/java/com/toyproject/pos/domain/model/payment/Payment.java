package com.toyproject.pos.domain.model.payment;

import java.math.BigDecimal;

public interface Payment {

    // 공통 정보
    CommonPaymentData info();

    // 실제 지불된 금액
    BigDecimal amountReceived();

    PaymentEnums.PaymentStatus getPaymentStatus();

    // 공통 조회 (info() 위임 편의)
    default Long paymentId() {
        return info().getPaymentId();
    }

    default String posNo() {
        return info().getPosNo();
    }

    default String tranNo() {
        return info().getTranNo();
    }

    default PaymentEnums.PaymentMethod method() {
        return info().getMethod();
    }

    default String requestedAt() {
        return info().getRequestedAt();
    }

    default String approvedAt() {
        return info().getApprovedAt();
    }

    // 금액 공통
    // 지불 해야할 금액
    default BigDecimal amountDue() {
        return info().getAmountDueToPay();
    }

    default Payment refund(BigDecimal amountToRefund) {
        throw new UnsupportedOperationException("이 결제수단은 refund 기본 구현이 없습니다.");
    }

}
