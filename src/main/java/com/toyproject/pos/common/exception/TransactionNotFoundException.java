package com.toyproject.pos.common.exception;

import com.toyproject.pos.domain.model.cart.CartKey;

// 거래 키로 저장된 판매를 찾지 못함 (조회·반품 원거래)
public class TransactionNotFoundException extends RuntimeException implements HasErrorCode {

    public TransactionNotFoundException(CartKey cartKey) {
        super("거래를 찾을 수 없습니다. strCd=" + cartKey.getStrCd()
                + ", posNo=" + cartKey.getPosNo()
                + ", tranNo=" + cartKey.getTranNo());
    }

    @Override
    public ErrorCode errorCode() {
        return ErrorCode.TRANSACTION_NOT_FOUND;
    }

}
