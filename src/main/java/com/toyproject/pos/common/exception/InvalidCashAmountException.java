package com.toyproject.pos.common.exception;

// 받은 금액이 0 이하
public class InvalidCashAmountException extends IllegalArgumentException implements HasErrorCode {

    public InvalidCashAmountException() {
        super("지불 금액이 이상 합니다. 확인 부탁 드립니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.INVALID_CASH_AMOUNT;
    }

}
