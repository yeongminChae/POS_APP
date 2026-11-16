package com.toyproject.pos.domain.dto.cart;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 장바구니 조회 응답. 키 이름은 CartSummaryResDto(SaleResDto.cart)와 같고, 도메인 Cart 의 내부 필드는 내보내지 않는다
@Getter
@AllArgsConstructor
@Builder
public class CartResDto {

    private final String strCd;
    private final String posNo;
    private final String tranNo;
    private final int totalQty;
    private final BigDecimal totalAmount;
    private final List<CartLineDto> lines; // 취소된 라인 포함 (itemStatus 로 구분)

    public static CartResDto of(Cart cart) {
        CartSummary summary = CartSummary.of(cart);
        return CartResDto.builder()
                .strCd(summary.getStrCd())
                .posNo(summary.getPosNo())
                .tranNo(summary.getTranNo())
                .totalQty(summary.getTotalQty())
                .totalAmount(summary.getTotalAmount())
                .lines(summary.getLines().stream().map(CartLineDto::of).toList())
                .build();
    }

}
