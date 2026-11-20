package com.toyproject.pos.domain.model.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 거래 키 — 점포 코드 + POS 번호 + 거래번호. 한 거래(장바구니·판매)를 식별한다.
 *
 * <ul>
 *   <li>인메모리 장바구니 저장소의 맵 키 (세 값이 모두 같아야 같은 장바구니)</li>
 *   <li>DB 판매({@code SALES})의 유일 키 (STORE_CD, POS_NO, TRAN_NO). 이미 결제된 키로 다시 결제하면 409 DUPLICATE_TRANSACTION</li>
 *   <li>요청에서는 {@code CartInfoReqDto}로 받고 {@code getKey()}로 꺼낸다</li>
 * </ul>
 * 같은 거래번호라도 POS 번호나 점포가 다르면 다른 거래다 (POS 여러 대가 같은 DB 를 쓰기 때문).
 */
@Getter
@AllArgsConstructor
@Builder
@EqualsAndHashCode // 인메모리 장바구니 저장소의 맵 키로 사용
public class CartKey {
    private final String strCd;   // 점포 코드
    private final String posNo;   // POS(또는 키오스크) 번호
    private final String tranNo;  // 거래번호

    /** 세 값으로 거래 키를 만든다. */
    public static CartKey of(String strCd, String posNo, String tranNo) {
        return CartKey.builder()
                .strCd(strCd)
                .posNo(posNo)
                .tranNo(tranNo)
                .build();
    }

}
