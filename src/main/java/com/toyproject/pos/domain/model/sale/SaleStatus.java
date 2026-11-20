package com.toyproject.pos.domain.model.sale;

/**
 * 판매 상태. DB {@code SALES.SALE_STATUS}에 이름 그대로 저장된다.
 */
public enum SaleStatus {
    /** 정상 판매 */
    NORMAL,
    /**
     * 반품됨. 두 곳에 쓰인다 — ① 반품된 원거래 (반품 시 조건부 UPDATE 로 NORMAL → REFUNDED, 이미 REFUNDED 면 409 ALREADY_REFUNDED)
     * ② 반품으로 새로 만든 반품 거래 자체 (반품의 반품도 같은 이유로 거절된다)
     */
    REFUNDED
}
