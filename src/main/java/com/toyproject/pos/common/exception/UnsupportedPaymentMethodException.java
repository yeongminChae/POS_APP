package com.toyproject.pos.common.exception;

/**
 * 지원하지 않는 결제 수단 (현재 카드 미구현) → 400 UNSUPPORTED_PAYMENT_METHOD.
 * 던지는 곳: {@code CheckoutServiceImpl.getPaymentSummary}.
 */
public class UnsupportedPaymentMethodException extends UnsupportedOperationException implements HasErrorCode {

    public UnsupportedPaymentMethodException(String message) {
        super(message);
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.UNSUPPORTED_PAYMENT_METHOD;
    }

}
