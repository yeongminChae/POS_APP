package com.toyproject.pos.domain.dto.cart;

import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class CartLineDto {
    private final String lineId;
    private final String barcode;
    private final String name;
    private final BigDecimal unitPrice;
    private final int qty;
    private final BigDecimal amount;
    private final ItemStatus itemStatus;

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
