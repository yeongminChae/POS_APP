package com.toyproject.pos.domain.dto.payment.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class PaymentReqDto {

    @JsonUnwrapped
    private CartInfoReqDto cartInfoReqDto;
    private PaymentEnums.PaymentMethod paymentMethod;
    private BigDecimal amountReceived;

    private CardReqDto cardReqDto; // method == CARD일 때만 채움

    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
