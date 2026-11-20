package com.toyproject.pos.common.exception;

/**
 * 담을 수 없는 상품의 공통 부모 (미등록 바코드 {@link ProductNotFoundException}, 판매중지 {@link ProductDiscontinuedException}).
 * 오류 코드는 자식 클래스가 정한다. (A4 이전에는 상품 담기가 이 예외를 잡아 "HTTP 200 + 바디 404"로 응답했지만,
 * 지금은 잡지 않고 전역 처리기가 404 / 409 로 응답한다)
 */
public abstract class ProductNotSellableException extends RuntimeException implements HasErrorCode {

    protected ProductNotSellableException(String message) {
        super(message);
    }

}
