package com.toyproject.pos.domain.model.cart;

/**
 * 장바구니 라인 상태. DB {@code SALE_LINES.ITEM_STATUS}에 이름 그대로 저장된다.
 * 상태별 수량·금액 규칙은 {@link CartLine} 참고.
 */
public enum ItemStatus {
    /** 정상 판매 라인 — 결제 합계·재고 차감 대상 */
    NORMAL,
    /** 지정 취소 라인 — 금액 0, 합계·재고 반영 제외, 영수증에는 남김 */
    CANCELLED,
    /** 반품 라인 — 수량·금액 음수, 재고 복원 대상 */
    REFUNDED,
    /** 라인 없음 ({@link CartLine#EMPTY} 전용, 저장되지 않음) */
    EMPTY
}
