package com.toyproject.pos.common.exception;

/**
 * 받은 금액이 결제 금액보다 적음 → 400 INSUFFICIENT_CASH.
 * 던지는 곳: {@code CashPayment.of} (거스름돈이 음수일 때).
 */
public class InsufficientCashException extends IllegalArgumentException implements HasErrorCode {

    public InsufficientCashException() {
        super("받은 금액이 결제 금액보다 적습니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.INSUFFICIENT_CASH;
    }

}
