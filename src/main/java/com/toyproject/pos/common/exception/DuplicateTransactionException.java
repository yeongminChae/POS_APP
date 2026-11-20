package com.toyproject.pos.common.exception;

/**
 * 이미 결제된 거래번호(점포·POS·거래번호)로 다시 결제 → 409 DUPLICATE_TRANSACTION.
 * 던지는 곳: {@code CheckoutServiceImpl.checkOut} 맨 앞 — 저장 전에 같은 거래 키의 SALES 행이 있는지 확인한다
 * (확인하지 않으면 DB UNIQUE 위반으로 원인이 드러나지 않는 500 이 났다, B11).
 */
public class DuplicateTransactionException extends IllegalStateException implements HasErrorCode {

    public DuplicateTransactionException() {
        super("이미 결제된 거래번호입니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.DUPLICATE_TRANSACTION;
    }

}
