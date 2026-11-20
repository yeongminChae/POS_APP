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
/**
 * 상품 요청 DTO — 담기({@code POST /api/carts/lines}), 일괄 담기({@code /lines/batch}의 배열 원소), 지정 취소({@code /lines/cancel})의 바디.
 * <pre>{"strCd":"S01","posNo":"01","tranNo":"1001","barcode":"8800000000001"}</pre>
 * 담기는 항상 수량 1이라 qty 필드가 없다 (보내도 무시됨).
 */
@Getter
@AllArgsConstructor
@Builder
public class ProductReqDto {

    @JsonUnwrapped
    @Valid
    private final CartInfoReqDto cartInfoReqDto;

    @NotBlank(message = "바코드는 필수입니다")
    private final String barcode;

    /** 거래 키 */
    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
