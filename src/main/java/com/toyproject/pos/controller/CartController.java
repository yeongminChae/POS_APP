package com.toyproject.pos.controller;

import com.toyproject.pos.domain.dto.cart.CartResDto;
import com.toyproject.pos.domain.dto.cart.CartSummaryResDto;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.product.request.UpdateQtyReqDto;
import com.toyproject.pos.service.interfaces.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/carts")
@Validated
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // 조회는 거래 키를 쿼리 파라미터로 받는다 (?strCd=&posNo=&tranNo=)
    @GetMapping
    public CartResDto getCurrentCart(@Valid @ModelAttribute CartInfoReqDto cartInfoReqDto) {
        return CartResDto.of(cartService.getCurrentCart(cartInfoReqDto));
    }

    // 결제 전 요약
    @GetMapping("/summary")
    public CartSummaryResDto getCartSummary(@Valid @ModelAttribute CartInfoReqDto cartInfoReqDto) {
        return CartSummaryResDto.of(cartService.getCartSummary(cartInfoReqDto.getKey()));
    }

    // 장바구니를 바꾸는 API 는 모두 200 + 바뀐 뒤의 장바구니(CartResDto). 실패는 예외 → 전역 처리기 (404 / 409 / 400)
    // *** 현재 임시 데이터 저장소 사용 -> 추후 실제 DB로 이주 계획
    @PostMapping("/lines")
    public CartResDto addToCart(@Valid @RequestBody ProductReqDto productReqDto) {
        return CartResDto.of(cartService.addItem(productReqDto));
    }

    // 다중 상품 일괄 등록 (전부 아니면 전무)
    @PostMapping("/lines/batch")
    public CartResDto addToCart(
            @RequestBody @NotEmpty(message = "담을 상품이 없습니다") List<@Valid ProductReqDto> productReqDtoList) {
        return CartResDto.of(cartService.addItems(productReqDtoList));
    }

    // 상품 수량 업데이트 (절대값)
    @PatchMapping("/lines")
    public CartResDto updatedProductQty(@Valid @RequestBody UpdateQtyReqDto updateQtyReqDto) {
        return CartResDto.of(cartService.updatedQty(updateQtyReqDto));
    }

    // 지정 취소 — 라인을 지우지 않고 CANCELLED 로 남기므로 DELETE 가 아닌 하위 경로
    @PostMapping("/lines/cancel")
    public CartResDto cancelLine(@Valid @RequestBody ProductReqDto productReqDto) {
        return CartResDto.of(cartService.cancelLine(productReqDto));
    }

}
