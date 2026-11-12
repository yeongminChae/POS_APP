package com.toyproject.pos.common.exception;

import com.toyproject.pos.domain.model.cart.CartKey;

// 거래 키로 저장된 판매를 찾지 못함 (조회·반품 원거래). API 변경(A4) 때 404로 매핑 예정
public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(CartKey cartKey) {
        super("거래를 찾을 수 없습니다. strCd=" + cartKey.getStrCd()
                + ", posNo=" + cartKey.getPosNo()
                + ", tranNo=" + cartKey.getTranNo());
    }

}
