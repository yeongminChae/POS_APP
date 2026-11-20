package com.toyproject.pos.common.exception;

/**
 * 받은 금액이 0 이하 → 400 INVALID_CASH_AMOUNT.
 * 던지는 곳: {@code CashPayment.of}. (받은 금액 누락은 이보다 앞서 요청 검증에서 400 VALIDATION_FAILED)
 */
public class InvalidCashAmountException extends IllegalArgumentException implements HasErrorCode {

    public InvalidCashAmountException() {
        super("지불 금액이 이상 합니다. 확인 부탁 드립니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.INVALID_CASH_AMOUNT;
    }

}
