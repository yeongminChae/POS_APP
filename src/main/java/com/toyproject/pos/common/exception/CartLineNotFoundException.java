package com.toyproject.pos.common.exception;

// 수량 변경·취소 대상 라인이 장바구니에 없음 (이미 취소된 라인 포함 — 도메인이 둘을 구분하지 않음)
public class CartLineNotFoundException extends RuntimeException implements HasErrorCode {

    public CartLineNotFoundException(String barcode) {
        super("장바구니에 없는 상품입니다. barcode=" + barcode);
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.CART_LINE_NOT_FOUND;
    }

}
