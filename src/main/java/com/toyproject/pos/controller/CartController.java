package com.toyproject.pos.controller;

import com.toyproject.pos.domain.dto.cart.CartResDto;
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
@RequestMapping("/api/cart")
@Validated
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/item")
    public CartResDto getCurrentCart(@Valid @RequestBody CartInfoReqDto cartInfoReqDto) {
        return CartResDto.of(cartService.getCurrentCart(cartInfoReqDto));
    }

    // 장바구니를 바꾸는 API 는 모두 200 + 바뀐 뒤의 장바구니(CartResDto). 실패는 예외 → 전역 처리기 (404 / 409 / 400)
    // *** 현재 임시 데이터 저장소 사용 -> 추후 실제 DB로 이주 계획
    @PostMapping("/item/add")
    public CartResDto addToCart(@Valid @RequestBody ProductReqDto productReqDto) {
        return CartResDto.of(cartService.addItem(productReqDto));
    }

    // 다중 상품 일괄 등록 (전부 아니면 전무)
    @PostMapping("/item/add/many")
    public CartResDto addToCart(
            @RequestBody @NotEmpty(message = "담을 상품이 없습니다") List<@Valid ProductReqDto> productReqDtoList) {
        return CartResDto.of(cartService.addItems(productReqDtoList));
    }

    // 상품 수량 업데이트 (절대값)
    @PostMapping("/item/update/qty")
    public CartResDto updatedProductQty(@Valid @RequestBody UpdateQtyReqDto updateQtyReqDto) {
        return CartResDto.of(cartService.updatedQty(updateQtyReqDto));
    }

    // 지정 취소
    @PostMapping("/item/cancel")
    public CartResDto cancelLine(@Valid @RequestBody ProductReqDto productReqDto) {
        return CartResDto.of(cartService.cancelLine(productReqDto));
    }

}
