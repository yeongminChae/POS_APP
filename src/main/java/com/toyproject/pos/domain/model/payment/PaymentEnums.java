package com.toyproject.pos.domain.model.payment;

public class PaymentEnums {

    // 결제 방법 구분
    public enum PaymentMethod {
        CASH,
        CARD
        /*, VOUCHER, POINT, ...*/
    }

    // 결제 상태
    public enum PaymentStatus {
        CREATED,
        APPROVED,
        DECLINED,
        REFUNDED
        /*, VOIDED*/
    }

}

