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

/**
 * 결제 요청 DTO — {@code POST /api/sales}의 바디.
 * <pre>{"strCd":"S01","posNo":"01","tranNo":"1001","paymentMethod":"CASH","amountReceived":20000}</pre>
 * 거래 키는 펼쳐진(@JsonUnwrapped) 최상위 키로 받는다. 결제 수단 필수, 현금이면 받은 금액 필수({@link CashAmountRequired}).
 */
@Getter
@AllArgsConstructor
@Builder
@CashAmountRequired
public class PaymentReqDto {

    // 결제할 장바구니의 거래 키 (판매도 이 키로 저장됨)
    @JsonUnwrapped
    @Valid
    private CartInfoReqDto cartInfoReqDto;

    @NotNull(message = "결제 수단은 필수입니다")
    private PaymentEnums.PaymentMethod paymentMethod;

    // 현금일 때만 필수 (@CashAmountRequired). 값 범위(≤ 0)는 도메인(CashPayment)에서 INVALID_CASH_AMOUNT 로 검사
    private BigDecimal amountReceived;

    private CardReqDto cardReqDto; // method == CARD일 때만 채움

    /** 거래 키 */
    public CartKey getKey() {
        return cartInfoReqDto.getKey();
    }

}
