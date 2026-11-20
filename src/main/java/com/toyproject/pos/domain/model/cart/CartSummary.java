package com.toyproject.pos.domain.model.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 장바구니 요약 — 특정 시점의 거래 키·총수량·총금액·라인 목록을 고정한 스냅샷 (불변).
 *
 * <p>{@link Cart}는 계속 바뀌는 작업 공간이고, 이 객체는 "이 시점의 장바구니"를 판매({@code Sale})에 담기 위한 값이다.</p>
 * <ul>
 *   <li>결제 전 요약 API ({@code GET /api/carts/summary})</li>
 *   <li>결제: {@code Sale}에 담겨 {@code SALES}·{@code SALE_LINES}로 저장되고 응답 {@code SaleResDto.cart}가 된다</li>
 *   <li>반품: 새 거래 키 + 반품 라인 합계({@link #refundOf})</li>
 * </ul>
 * 합계는 상태 기준이다 — 일반 요약은 정상 라인, 반품 요약은 반품 라인만 더한다. 라인 목록은 취소 라인도 포함한다.
 */
@Getter
@AllArgsConstructor
@Builder
public class CartSummary {

    private final String strCd;            // 점포 코드
    private final String posNo;            // POS 번호
    private final String tranNo;           // 거래번호
    private final int totalQty;            // 총수량 (일반: 정상 라인 합, 반품: 반품 라인 합 = 음수)
    private final BigDecimal totalAmount;  // 총금액 (같은 기준, 반품은 음수)
    private final List<CartLine> lines;    // 라인 목록 (lineId 포함, 취소 라인 포함)

    /** 일반 요약: 장바구니 자신의 거래 키 + 정상(NORMAL) 라인 합계. 요약 API·결제가 사용한다. */
    public static CartSummary of(Cart cart) {
        return create(cart.getStrCd(), cart.getPosNo(), cart.getTranNo(), cart, false);
    }

    /**
     * 반품 요약: 새 거래 키 + REFUNDED 라인 합계.
     * 원거래를 복원해 {@link Cart#refundAll()}한 장바구니를, 반품 거래의 새 키로 저장하기 위해 쓴다.
     *
     * @param cart    반품 처리된 원거래 장바구니
     * @param cartKey 반품 거래의 새 거래 키 (요청의 strCd·posNo·tranNo)
     */
    public static CartSummary refundOf(Cart cart, CartKey cartKey) {
        return create(cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo(), cart, true);
    }

    /**
     * DB 조회 결과로 복원: 키·라인은 그대로, 합계는 저장된 값.
     * 라인에서 다시 계산하지 않고 {@code SALES}에 저장된 합계를 그대로 써야, 조회 결과가 결제 당시 기록과 일치한다.
     */
    public static CartSummary restoredOf(CartSummary summary, int totalQty, BigDecimal totalAmount) {
        return CartSummary.builder()
                .strCd(summary.getStrCd())
                .posNo(summary.getPosNo())
                .tranNo(summary.getTranNo())
                .totalQty(totalQty)
                .totalAmount(totalAmount)
                .lines(summary.getLines())
                .build();
    }

    /** 공통 생성: {@code isRefunded}에 따라 정상 라인 또는 반품 라인의 합계를 계산한다. */
    private static CartSummary create(
            String strCd, String posNo, String tranNo, Cart cart, boolean isRefunded
    ) {
        return CartSummary.builder()
                .strCd(strCd)
                .posNo(posNo)
                .tranNo(tranNo)
                .totalAmount(cart.getTotalAmountInLines(isRefunded))
                .totalQty(cart.getTotalQtyInLines(isRefunded))
                .lines(cart.getLinesList())
                .build()
                ;
    }

}
