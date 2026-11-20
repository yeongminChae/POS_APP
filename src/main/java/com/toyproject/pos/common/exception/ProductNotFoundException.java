package com.toyproject.pos.common.exception;

/**
 * 미등록 바코드 → 404 PRODUCT_NOT_FOUND.
 * 던지는 곳: {@code CartServiceImpl.getProduct} (바코드로 상품을 찾지 못함).
 */
public class ProductNotFoundException extends ProductNotSellableException {

    public ProductNotFoundException(String barcode) {
        super("상품을 찾을 수 없습니다. barcode=" + barcode);
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.PRODUCT_NOT_FOUND;
    }

}
