package com.toyproject.pos.common.exception;

// 이미 결제된 거래번호(점포·POS·거래번호)로 다시 결제
public class DuplicateTransactionException extends IllegalStateException implements HasErrorCode {

    public DuplicateTransactionException() {
        super("이미 결제된 거래번호입니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.DUPLICATE_TRANSACTION;
    }

}
