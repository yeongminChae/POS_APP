package com.toyproject.pos.common.exception;

// 미등록 바코드
public class ProductNotFoundException extends ProductNotSellableException {

    public ProductNotFoundException(String barcode) {
        super("상품을 찾을 수 없습니다. barcode=" + barcode);
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.PRODUCT_NOT_FOUND;
    }

}
