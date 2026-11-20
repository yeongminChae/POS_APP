package com.toyproject.pos.domain.model.payment;

import com.toyproject.pos.domain.model.cart.CartKey;

import java.math.BigDecimal;

/**
 * 결제 — 결제 수단별 구현이 공통으로 따르는 인터페이스.
 *
 * <p>공통 정보는 {@link CommonPaymentData}({@link #info()})에 있고, 나머지 조회 메서드는 그 값을 꺼내 주는 편의 메서드다.
 * 현재 구현은 {@link CashPayment}뿐이다. 카드 결제를 추가하면 이 인터페이스를 구현하고,
 * 저장({@code CheckoutRepositoryImpl.save})·응답({@code PaymentResDto})·복원({@code SaleRowMapper})에 분기를 더해야 한다.</p>
 */
public interface Payment {

    // 공통 정보
    /** 결제 공통 정보 (거래 키, 청구액, 수단, 시각) */
    CommonPaymentData info();

    // 실제 지불된 금액
    /** 고객에게 받은 금액. 현금이 아닌 수단은 null 일 수 있다 */
    BigDecimal amountReceived();

    /** 결제 상태 (APPROVED, REFUNDED 등) */
    PaymentEnums.PaymentStatus getPaymentStatus();

    // 공통 조회 (info() 위임 편의)
    /** 결제 ID (저장 전에는 null) */
    default Long paymentId() {
        return info().getPaymentId();
    }

    /** POS 번호 */
    default String posNo() {
        return info().getPosNo();
    }

    /** 거래번호 */
    default String tranNo() {
        return info().getTranNo();
    }

    /** 결제 수단 */
    default PaymentEnums.PaymentMethod method() {
        return info().getMethod();
    }

    /** 결제 요청 시각 */
    default String requestedAt() {
        return info().getRequestedAt();
    }

    /** 결제 승인 시각 — 판매 응답의 approvedAt 으로도 쓰인다 */
    default String approvedAt() {
        return info().getApprovedAt();
    }

    // 금액 공통
    // 지불 해야할 금액
    /** 청구액 (정상 라인 합계, 반품은 음수) */
    default BigDecimal amountDue() {
        return info().getAmountDueToPay();
    }

    /**
     * 이 결제를 바탕으로 반품 결제를 만든다. 수단마다 환불 방식이 달라 구현체가 재정의한다.
     * 재정의하지 않은 수단은 반품할 수 없다 (500).
     *
     * @param amountToRefund 환불할 금액 (양수)
     * @param refundKey      반품 거래의 새 거래 키
     */
    default Payment refund(BigDecimal amountToRefund, CartKey refundKey) {
        throw new UnsupportedOperationException("이 결제수단은 refund 기본 구현이 없습니다.");
    }

}
