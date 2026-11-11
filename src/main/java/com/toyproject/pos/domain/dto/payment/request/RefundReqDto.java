package com.toyproject.pos.domain.dto.payment.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class RefundReqDto {

    @JsonUnwrapped
    private CartInfoReqDto cartInfoReqDto;
    // 원거래 참조
    private final String origStrCd;   // 원거래 POS 번호
    private final String origPosNo;   // 원거래 POS 번호
    private final String origTranNo;  // 원거래 거래 번호

    private final String reason;      // 환불 사유 (선택)

    public CartKey getRefundInfoKey() {
        return CartKey.of(this.origStrCd, this.origPosNo, this.origTranNo);
    }

}
