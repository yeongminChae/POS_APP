package com.toyproject.pos.domain.model.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CartKey {
    private final String strCd;
    private final String posNo;
    private final String tranNo;

    public static CartKey of(String strCd, String posNo, String tranNo) {
        return CartKey.builder()
                .strCd(strCd)
                .posNo(posNo)
                .tranNo(tranNo)
                .build();
    }

}
