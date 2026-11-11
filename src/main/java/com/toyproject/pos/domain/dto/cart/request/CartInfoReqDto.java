package com.toyproject.pos.domain.dto.cart.request;

import com.toyproject.pos.domain.model.cart.CartKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CartInfoReqDto {

    private final String strCd;
    private final String posNo;
    private final String tranNo;

    public CartKey getKey() {
        return CartKey.of(this.strCd, this.posNo, this.tranNo);
    }

}
