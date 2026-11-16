package com.toyproject.pos.common.exception;

// 판매중지(discontinued) 상품
public class ProductDiscontinuedException extends ProductNotSellableException {

    public ProductDiscontinuedException(String barcode) {
        super("판매중지 상품입니다. barcode=" + barcode);
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.PRODUCT_DISCONTINUED;
    }

}
