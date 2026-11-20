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

/**
 * 판매 API — {@code /api/sales} (결제·거래 조회·반품, 명세: docs/api.md).
 * 결제와 반품은 모두 "새 판매를 만든다"는 의미라 POST 다. 업무 흐름은 {@code CheckoutServiceImpl}에 있다.
 * 성공은 200 + {@link SaleResDto}, 실패는 {@code GlobalExceptionHandler}가 400/404/409 로 응답한다.
 */
@RestController
@RequestMapping("/api/sales")
@Validated
@RequiredArgsConstructor
public class SaleController {

    private final CheckoutService checkoutService;

    // 거래 조회. 거래 키는 쿼리 파라미터 (?strCd=&posNo=&tranNo=)
    /** {@code GET /api/sales} — 저장된 거래 조회. 없으면 404 TRANSACTION_NOT_FOUND. */
    @GetMapping
    public SaleResDto getTransaction(@Valid @ModelAttribute CartInfoReqDto cartInfoReqDto) {
        return checkoutService.callTransaction(cartInfoReqDto);
    }

    // 결제 = 판매 생성
    /**
     * {@code POST /api/sales} — 현재 장바구니를 결제한다 (판매 저장 + 재고 차감, 커밋 후 장바구니 삭제).
     * 빈 카트·받은 금액 오류·카드 400, 이미 결제된 거래번호 409.
     */
    @PostMapping
    public SaleResDto checkOut(@Valid @RequestBody PaymentReqDto paymentReq) {
        return checkoutService.checkOut(paymentReq);
    }

    // 반품 = 반품 거래(새 판매) 생성
    /**
     * {@code POST /api/sales/refunds} — 원거래 전체를 반품하고 새 거래 키로 반품 거래를 저장한다 (재고 복원).
     * 원거래 없음 404, 이미 반품됨 409.
     */
    @PostMapping("/refunds")
    public SaleResDto refundTransaction(@Valid @RequestBody RefundReqDto refundReqDto) {
        return checkoutService.refundTransaction(refundReqDto);
    }

}
