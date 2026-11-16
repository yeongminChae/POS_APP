package com.toyproject.pos.domain.dto.payment.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class RefundReqDto {

    // 반품 거래의 새 거래 키
    @JsonUnwrapped
    @Valid
    private CartInfoReqDto cartInfoReqDto;

    // 원거래 참조
    @NotBlank(message = "원거래 점포 코드는 필수입니다")
    private final String origStrCd;   // 원거래 점포 코드
    @NotBlank(message = "원거래 POS 번호는 필수입니다")
    private final String origPosNo;   // 원거래 POS 번호
    @NotBlank(message = "원거래 거래번호는 필수입니다")
    private final String origTranNo;  // 원거래 거래 번호

    @Size(max = 100, message = "반품 사유는 100자 이하여야 합니다")
    private final String reason;      // 환불 사유 (선택, TR_JSON 에만 저장)

    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

    public CartKey getRefundInfoKey() {
        return CartKey.of(this.origStrCd, this.origPosNo, this.origTranNo);
    }

}
