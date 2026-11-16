package com.toyproject.pos.common.exception;

import org.springframework.http.HttpStatus;

// API 오류 코드 (docs/api-change-plan.md 2.2절). 응답의 ProblemDetail.code 와 HTTP 상태를 정한다
public enum ErrorCode {

    // 400 — 요청 값 문제
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_CASH_AMOUNT(HttpStatus.BAD_REQUEST),
    INSUFFICIENT_CASH(HttpStatus.BAD_REQUEST),
    EMPTY_CART(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_PAYMENT_METHOD(HttpStatus.BAD_REQUEST),

    // 404 — 대상 없음
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    CART_LINE_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND),

    // 409 — 대상의 현재 상태와 충돌
    PRODUCT_DISCONTINUED(HttpStatus.CONFLICT),
    ALREADY_REFUNDED(HttpStatus.CONFLICT),
    DUPLICATE_TRANSACTION(HttpStatus.CONFLICT),

    // 500 — 그 외
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

}
