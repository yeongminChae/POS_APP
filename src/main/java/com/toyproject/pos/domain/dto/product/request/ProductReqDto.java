package com.toyproject.pos.domain.dto.product.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ProductReqDto {

    @JsonUnwrapped
    private final CartInfoReqDto cartInfoReqDto;
    private final String barcode;

    @Builder.Default
    private final Integer qty = 1;

    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
