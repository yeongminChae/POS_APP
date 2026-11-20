package com.toyproject.pos.common.exception;

/**
 * 이미 반품된 거래(또는 반품 거래 자체)를 다시 반품 → 409 ALREADY_REFUNDED.
 * 던지는 곳: {@code CheckoutServiceImpl.refundTransaction} — 원거래 상태를 REFUNDED 로 바꾸는 조건부 UPDATE 가 0행일 때.
 */
public class AlreadyRefundedException extends IllegalStateException implements HasErrorCode {

    public AlreadyRefundedException() {
        super("이미 반품된 거래입니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.ALREADY_REFUNDED;
    }

}
