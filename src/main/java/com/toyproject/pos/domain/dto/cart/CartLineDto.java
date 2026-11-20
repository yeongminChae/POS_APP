package com.toyproject.pos.domain.dto.cart;

import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 응답용 장바구니 라인 — {@link CartResDto}·{@link CartSummaryResDto}의 {@code lines} 원소.
 * 도메인 {@link CartLine}에서 내부용 상품 ID(productId)만 빼고 그대로 옮긴다.
 */
@Getter
@AllArgsConstructor
@Builder
public class CartLineDto {
    private final String lineId;          // 라인 번호 "0001"…
    private final String barcode;         // 바코드
    private final String name;            // 상품명
    private final BigDecimal unitPrice;   // 단가
    private final int qty;                // 수량 (반품 라인은 음수)
    private final BigDecimal amount;      // 금액 (취소 0, 반품 음수)
    private final ItemStatus itemStatus;  // NORMAL / CANCELLED / REFUNDED

    /** 도메인 라인 → 응답 라인 */
    public static CartLineDto of(CartLine cartLine) {
        return CartLineDto.builder()
                .lineId(cartLine.getLineId())
                .barcode(cartLine.getBarcode())
                .name(cartLine.getName())
                .unitPrice(cartLine.getUnitPrice())
                .qty(cartLine.getQty())
                .amount(cartLine.getAmount())
                .itemStatus(cartLine.getItemStatus())
                .build()
                ;
    }

}
