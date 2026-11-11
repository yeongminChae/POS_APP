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
        return CartSummary.builder()
                .strCd(cart.getStrCd())
                .posNo(cart.getPosNo())
                .tranNo(cart.getTranNo())
                .totalAmount(cart.getTotalAmountInLines())
                .totalQty(cart.getTotalQtyInLines())
                .lines(cart.getLinesList())
                .build()
                ;

    }

    public CartSummary refundOf(Cart cart, CartKey cartKey) {
        return CartSummary.builder()
                .strCd(cartKey.getStrCd())
                .posNo(cartKey.getPosNo())
                .tranNo(cartKey.getTranNo())
                .totalAmount(cart.getTotalAmountInLines(true))
                .totalQty(cart.getTotalQtyInLines(true))
                .lines(cart.getLinesList())
                .build()
                ;

    }

}
