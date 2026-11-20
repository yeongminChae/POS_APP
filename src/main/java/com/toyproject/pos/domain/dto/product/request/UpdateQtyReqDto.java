package com.toyproject.pos.domain.dto.product.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 수량 변경 요청 (담기·취소와 달리 qty 필수)
/**
 * 수량 변경 요청 DTO — {@code PATCH /api/carts/lines}의 바디.
 * <pre>{"strCd":"S01","posNo":"01","tranNo":"1001","barcode":"8800000000001","qty":3}</pre>
 * qty 는 "몇 개 더"가 아니라 바꿀 최종 수량(절대값)이다. 누락 400, 1 미만 400.
 */
@Getter
@AllArgsConstructor
@Builder
public class UpdateQtyReqDto {

    @JsonUnwrapped
    @Valid
    private final CartInfoReqDto cartInfoReqDto;

    @NotBlank(message = "바코드는 필수입니다")
    private final String barcode;

    @NotNull(message = "수량은 필수입니다")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다")
    private final Integer qty;

    /** 거래 키 */
    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
