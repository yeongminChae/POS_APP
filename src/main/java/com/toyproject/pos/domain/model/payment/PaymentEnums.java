package com.toyproject.pos.domain.model.payment;

/**
 * 결제 관련 열거형 모음. DB {@code PAYMENTS.METHOD}·{@code PAYMENTS.STATUS}에 이름 그대로 저장된다.
 * 주의: DB CHECK 제약과 값 목록이 어긋나는 문제가 알려져 있다 ({@code docs/schema-issues.md}).
 */
public class PaymentEnums {

    // 결제 방법 구분
    /** 결제 수단. 요청에 없는 값을 보내면 400 MALFORMED_REQUEST */
    public enum PaymentMethod {
        /** 현금 — 유일하게 구현된 수단 */
        CASH,
        /** 카드 — 미구현, 요청하면 400 UNSUPPORTED_PAYMENT_METHOD */
        CARD
        /*, VOUCHER, POINT, ...*/
    }

    // 결제 상태
    /** 결제 상태 */
    public enum PaymentStatus {
        /** 생성됨 (현재 사용하지 않음 — 승인 절차가 있는 수단용) */
        CREATED,
        /** 승인됨 — 현금 결제는 생성 즉시 이 상태 */
        APPROVED,
        /** 거절됨 (현재 사용하지 않음) */
        DECLINED,
        /** 반품 결제 */
        REFUNDED
        /*, VOIDED*/
    }

}
