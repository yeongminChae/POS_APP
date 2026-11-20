package com.toyproject.pos.common.exception;

/**
 * 판매중지(discontinued) 상품을 담으려 함 → 409 PRODUCT_DISCONTINUED.
 * 던지는 곳: {@code CartServiceImpl.getProduct}. 발주중단(suspend_order)·상태 NULL 은 담을 수 있다.
 */
public class ProductDiscontinuedException extends ProductNotSellableException {

    public ProductDiscontinuedException(String barcode) {
        super("판매중지 상품입니다. barcode=" + barcode);
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.PRODUCT_DISCONTINUED;
    }

}
