package com.toyproject.pos.common.exception;

/**
 * 결제할 정상(NORMAL) 라인이 없음 (빈 카트, 전부 취소한 카트) → 400 EMPTY_CART.
 * 던지는 곳: {@code CheckoutServiceImpl.saleModelBuilder}.
 */
public class EmptyCartException extends IllegalStateException implements HasErrorCode {

    public EmptyCartException() {
        super("결제할 상품이 없습니다.");
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.EMPTY_CART;
    }

}
