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

/**
 * 반품 요청 DTO — {@code POST /api/sales/refunds}의 바디.
 * <pre>{"strCd":"S01","posNo":"01","tranNo":"2001","origStrCd":"S01","origPosNo":"01","origTranNo":"1001","reason":"고객 변심"}</pre>
 * 거래 키가 두 벌이다 — strCd·posNo·tranNo 는 <b>반품 거래의 새 키</b>(이 키로 반품 판매가 저장됨), orig* 는 반품할 원거래 키.
 * 반품은 원거래 전체를 대상으로 하므로 라인 지정은 없다.
 */
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

    /** 반품 거래의 새 거래 키 */
    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

    /** 반품할 원거래의 거래 키 */
    public CartKey getRefundInfoKey() {
        return CartKey.of(this.origStrCd, this.origPosNo, this.origTranNo);
    }

}
