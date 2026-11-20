package com.toyproject.pos.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 오류 코드 (docs/api-change-plan.md 2.2절). 응답의 ProblemDetail.code 와 HTTP 상태를 정한다.
 *
 * <p>상태는 세 가지만 쓴다: 400 요청 값이 틀림 / 404 대상이 없음 / 409 대상의 현재 상태와 충돌. 그 외는 500.
 * 클라이언트(POS·키오스크)는 HTTP 상태가 아니라 이 이름({@code code})으로 화면 처리를 나눈다 — 이름을 바꾸면 API 계약이 바뀐다.</p>
 */
public enum ErrorCode {

    // 400 — 요청 값 문제
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),          // 요청 DTO 검증 실패 (필수 값 누락 등) — errors[] 포함
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),          // JSON 문법 오류, 없는 enum 값
    INVALID_CASH_AMOUNT(HttpStatus.BAD_REQUEST),        // 받은 금액 ≤ 0
    INSUFFICIENT_CASH(HttpStatus.BAD_REQUEST),          // 받은 금액 < 결제액
    EMPTY_CART(HttpStatus.BAD_REQUEST),                 // 결제할 정상 라인 없음
    UNSUPPORTED_PAYMENT_METHOD(HttpStatus.BAD_REQUEST), // 카드 등 미구현 결제 수단

    // 404 — 대상 없음
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),            // 미등록 바코드
    CART_LINE_NOT_FOUND(HttpStatus.NOT_FOUND),          // 수량 변경·취소 대상 라인 없음
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND),        // 없는 거래 조회·반품

    // 409 — 대상의 현재 상태와 충돌
    PRODUCT_DISCONTINUED(HttpStatus.CONFLICT),          // 판매중지 상품 담기
    ALREADY_REFUNDED(HttpStatus.CONFLICT),              // 이미 반품된 거래
    DUPLICATE_TRANSACTION(HttpStatus.CONFLICT),         // 이미 결제된 거래번호

    // 500 — 그 외
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);   // 예상하지 못한 오류 (detail 고정, 원인은 로그)

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /** 이 코드로 응답할 HTTP 상태 */
    public HttpStatus status() {
        return status;
    }

}
