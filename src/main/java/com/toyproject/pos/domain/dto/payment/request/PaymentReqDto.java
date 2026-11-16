package com.toyproject.pos.domain.dto.payment.request;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.payment.PaymentEnums;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
@CashAmountRequired
public class PaymentReqDto {

    @JsonUnwrapped
    @Valid
    private CartInfoReqDto cartInfoReqDto;

    @NotNull(message = "결제 수단은 필수입니다")
    private PaymentEnums.PaymentMethod paymentMethod;

    // 현금일 때만 필수 (@CashAmountRequired). 값 범위(≤ 0)는 도메인(CashPayment)에서 INVALID_CASH_AMOUNT 로 검사
    private BigDecimal amountReceived;

    private CardReqDto cardReqDto; // method == CARD일 때만 채움

    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
