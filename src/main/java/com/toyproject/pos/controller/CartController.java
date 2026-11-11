package com.toyproject.pos.controller;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.response.ApiResponseDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.service.interfaces.CartService;
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
    public Cart getCurrentCart(@RequestBody CartInfoReqDto cartInfoReqDto) {
        return cartService.getCurrentCart(cartInfoReqDto);
    }

    // 상품 조회 및 데이터 저장
    // *** 현재 임시 데이터 저장소 사용 -> 추후 실제 DB로 이주 계획
    @PostMapping("/item/add")
    public ResponseEntity<ApiResponseDto> addToCart(@RequestBody ProductReqDto productReqDto) {
        boolean result = cartService.addItem(productReqDto);

        return ResponseEntity.ok(new ApiResponseDto(
                        result ? "상품 추가 성공" : "미등록 상품",
                        result ? HttpStatus.OK.value() : HttpStatus.NOT_FOUND.value()
                )
        );

    }

    // 다중 상품 일괄 등록
    @PostMapping("/item/add/many")
    public ResponseEntity<ApiResponseDto> addToCart(@RequestBody List<ProductReqDto> productReqDtoList) {

        int count = 0;
        for (ProductReqDto productReqDto : productReqDtoList)
            count += (cartService.addItem(productReqDto) ? 1 : 0);

        boolean result = count == (long) productReqDtoList.size();

        return ResponseEntity.ok(new ApiResponseDto(
                        result ? "상품 추가 성공" : "미등록 상품",
                        result ? HttpStatus.OK.value() : HttpStatus.NOT_FOUND.value()
                )
        );

    }

    // 상품 수량 업데이트
    @PostMapping("/item/update/qty")
    public boolean updatedProductQty(@RequestBody ProductReqDto productReqDto) {
        if (productReqDto.getQty() > 1) {
            // todo
            // "qty 1이상일 때만 정상 처리"
        }

        return cartService.updatedQty(productReqDto);

    }

    // 지정 취소
    @PostMapping("/item/cancel")
    public boolean cancelLine(@RequestBody ProductReqDto productReqDto) {
        return cartService.cancelLine(productReqDto);

    }

}
