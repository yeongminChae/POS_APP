package com.toyproject.pos.common.exception;

// 이미 반품된 거래(또는 반품 거래 자체)를 다시 반품
public class AlreadyRefundedException extends IllegalStateException implements HasErrorCode {

    public AlreadyRefundedException() {
        super("이미 반품된 거래입니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.ALREADY_REFUNDED;
    }

}
