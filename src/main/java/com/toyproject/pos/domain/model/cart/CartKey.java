package com.toyproject.pos.domain.model.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
@EqualsAndHashCode // 인메모리 장바구니 저장소의 맵 키로 사용
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
