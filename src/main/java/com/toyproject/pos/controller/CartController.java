package com.toyproject.pos.controller;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.product.request.UpdateQtyReqDto;
import com.toyproject.pos.domain.dto.response.ApiResponseDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.service.interfaces.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public Cart getCurrentCart(@Valid @RequestBody CartInfoReqDto cartInfoReqDto) {
        return cartService.getCurrentCart(cartInfoReqDto);
    }

    // 상품 조회 및 데이터 저장
    // *** 현재 임시 데이터 저장소 사용 -> 추후 실제 DB로 이주 계획
    @PostMapping("/item/add")
    public ResponseEntity<ApiResponseDto> addToCart(@Valid @RequestBody ProductReqDto productReqDto) {
        return toAddItemResponse(cartService.addItem(productReqDto));
    }

    // 다중 상품 일괄 등록
    @PostMapping("/item/add/many")
    public ResponseEntity<ApiResponseDto> addToCart(
            @RequestBody @NotEmpty(message = "담을 상품이 없습니다") List<@Valid ProductReqDto> productReqDtoList) {
        return toAddItemResponse(cartService.addItems(productReqDtoList));
    }

    // 상품 수량 업데이트
    @PostMapping("/item/update/qty")
    public boolean updatedProductQty(@Valid @RequestBody UpdateQtyReqDto updateQtyReqDto) {
        return cartService.updatedQty(updateQtyReqDto);
    }

    // 지정 취소
    @PostMapping("/item/cancel")
    public boolean cancelLine(@Valid @RequestBody ProductReqDto productReqDto) {
        return cartService.cancelLine(productReqDto);

    }

    // 상품 추가 성공 응답. 실패(미등록·판매중지)는 예외로 전역 처리기가 404 / 409로 응답
    // (응답 바디는 A3 응답 형식 통일 때 변경 후 장바구니로 바뀜)
    private static ResponseEntity<ApiResponseDto> toAddItemResponse(boolean result) {
        return ResponseEntity.ok(new ApiResponseDto("상품 추가 성공", HttpStatus.OK.value()));
    }

}
