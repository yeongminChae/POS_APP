package com.toyproject.pos.controller;

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
@RequestMapping("/api/sales")
@Validated
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    // 거래 조회. 거래 키는 쿼리 파라미터 (?strCd=&posNo=&tranNo=)
    @GetMapping
    public SaleResDto getTransaction(@Valid @ModelAttribute CartInfoReqDto cartInfoReqDto) {
        return checkoutService.callTransaction(cartInfoReqDto);
    }

    // 결제 = 판매 생성
    @PostMapping
    public SaleResDto checkOut(@Valid @RequestBody PaymentReqDto paymentReq) {
        return checkoutService.checkOut(paymentReq);
    }

    // 반품 = 반품 거래(새 판매) 생성
    @PostMapping("/refunds")
    public SaleResDto refundTransaction(@Valid @RequestBody RefundReqDto refundReqDto) {
        return checkoutService.refundTransaction(refundReqDto);
    }

}
