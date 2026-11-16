package com.toyproject.pos.domain.dto.cart;


import com.toyproject.pos.domain.model.cart.CartSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// 장바구니 요약 (결제 요약 API, SaleResDto.cart)
@Getter
@AllArgsConstructor
@Builder
public class CartSummaryResDto {

    private final String strCd;
    private final String posNo;
    private final String tranNo;
    private final int totalQty;
    private final BigDecimal totalAmount;
    private final List<CartLineDto> lines;

    public static CartSummaryResDto of(CartSummary summary) {
        return CartSummaryResDto.builder()
                .strCd(summary.getStrCd())
                .posNo(summary.getPosNo())
                .tranNo(summary.getTranNo())
                .totalQty(summary.getTotalQty())
                .totalAmount(summary.getTotalAmount())
                .lines(getCartLineDtoList(summary))
                .build();
    }

    private static List<CartLineDto> getCartLineDtoList(CartSummary summary) {
        return summary.getLines().stream()
                .map(cartLine -> CartLineDto.of(cartLine))
                .collect(Collectors.toUnmodifiableList());

    }


}
