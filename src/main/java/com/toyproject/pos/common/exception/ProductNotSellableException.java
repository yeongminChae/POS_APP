package com.toyproject.pos.common.exception;

// 담을 수 없는 상품의 공통 부모 (미등록 바코드, 판매중지). 상품 담기에서 false 로 응답하는 유일한 경우
public abstract class ProductNotSellableException extends RuntimeException implements HasErrorCode {

    protected ProductNotSellableException(String message) {
        super(message);
    }

}
