package com.toyproject.pos.controller;

import com.toyproject.pos.domain.dto.cart.CartSummaryResDto;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.dto.payment.request.RefundReqDto;
import com.toyproject.pos.domain.dto.sale.SaleResDto;
import com.toyproject.pos.service.interfaces.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@Validated
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    // 요청은 거래 키만 쓰므로 PaymentReqDto 전체를 검증하지 않는다 (A1·A2에서 쿼리 파라미터로 바뀔 때 키 검증)
    @GetMapping("/{posNo}/{tranNo}/summary")
    public CartSummaryResDto getSaleSummary(@RequestBody PaymentReqDto paymentReq) {
        return CartSummaryResDto.of(checkoutService.getSaleSummary(paymentReq));

    }

    // get 요청은 관례상 RequestBody 사용X
    @GetMapping("/transaction/call")
    public SaleResDto getTransaction(@Valid @RequestBody CartInfoReqDto cartInfoReqDto) {
        return checkoutService.callTransaction(cartInfoReqDto);
    }

    @PostMapping
    public SaleResDto checkOut(@Valid @RequestBody PaymentReqDto paymentReq) {
        return checkoutService.checkOut(paymentReq);
    }

    @PostMapping("/transaction/refund")
    public SaleResDto refundTransaction(@Valid @RequestBody RefundReqDto refundReqDto) {
        return checkoutService.refundTransaction(refundReqDto);
    }

}
