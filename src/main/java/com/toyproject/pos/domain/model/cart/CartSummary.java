package com.toyproject.pos.domain.model.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class CartSummary {

    private final String strCd;
    private final String posNo;
    private final String tranNo;
    private final int totalQty;
    private final BigDecimal totalAmount;
    private final List<CartLine> lines;

    public static CartSummary of(Cart cart) {
        return create(cart.getStrCd(), cart.getPosNo(), cart.getTranNo(), cart, false);
    }

    // 반품 요약: 새 거래 키 + REFUNDED 라인 합계
    public static CartSummary refundOf(Cart cart, CartKey cartKey) {
        return create(cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo(), cart, true);
    }

    // DB 조회 결과로 복원: 키·라인은 그대로, 합계는 저장된 값
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
