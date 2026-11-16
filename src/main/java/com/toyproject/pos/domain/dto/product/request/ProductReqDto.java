package com.toyproject.pos.domain.dto.product.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 상품 담기·취소 요청. 수량 변경은 UpdateQtyReqDto
@Getter
@AllArgsConstructor
@Builder
public class ProductReqDto {

    @JsonUnwrapped
    @Valid
    private final CartInfoReqDto cartInfoReqDto;

    @NotBlank(message = "바코드는 필수입니다")
    private final String barcode;

    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
