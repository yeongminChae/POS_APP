package com.toyproject.pos.controller;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.dto.payment.request.RefundReqDto;
import com.toyproject.pos.domain.dto.sale.SaleResDto;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.service.interfaces.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/checkout")
@Validated
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @GetMapping("/{posNo}/{tranNo}/summary")
    public CartSummary getSaleSummary(@RequestBody PaymentReqDto paymentReq) {
        return checkoutService.getSaleSummary(paymentReq);

    }

    // get 요청은 관례상 RequestBody 사용X
    @GetMapping("/transaction/call")
    public SaleResDto getTransaction(@RequestBody CartInfoReqDto cartInfoReqDto) {
        return checkoutService.callTransaction(cartInfoReqDto);
    }

    @PostMapping
    public SaleResDto checkOut(@RequestBody PaymentReqDto paymentReq) {
        return checkoutService.checkOut(paymentReq);
    }

    @PostMapping("/transaction/refund")
    public SaleResDto refundTransaction(@RequestBody RefundReqDto refundReqDto) {
        return checkoutService.refundTransaction(refundReqDto);
    }

}
