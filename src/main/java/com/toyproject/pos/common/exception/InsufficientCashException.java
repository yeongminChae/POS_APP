package com.toyproject.pos.common.exception;

// 받은 금액이 결제 금액보다 적음
public class InsufficientCashException extends IllegalArgumentException implements HasErrorCode {

    public InsufficientCashException() {
        super("받은 금액이 결제 금액보다 적습니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.INSUFFICIENT_CASH;
    }

}
