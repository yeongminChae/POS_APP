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

/**
 * 장바구니 API — {@code /api/carts} (명세: docs/api.md).
 *
 * <ul>
 *   <li>요청 → {@code @Valid} 검증(실패 400) → {@link CartService} → 도메인 {@code Cart} → {@link CartResDto}로 변환해 200 응답</li>
 *   <li>실패는 서비스가 던진 예외를 {@code GlobalExceptionHandler}가 ProblemDetail 로 바꾼다 (404 상품·라인 없음, 409 판매중지)</li>
 *   <li>GET 은 거래 키를 쿼리 파라미터로, 나머지는 JSON 바디로 받는다</li>
 *   <li>클래스의 {@code @Validated}는 목록 바디({@code List<@Valid ...>}, {@code @NotEmpty}) 검증에 필요하다</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/carts")
@Validated
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    // 조회는 거래 키를 쿼리 파라미터로 받는다 (?strCd=&posNo=&tranNo=)
    /** {@code GET /api/carts} — 장바구니 조회. 없는 장바구니는 빈 장바구니로 만들어 돌려준다 (거래 시작을 겸함). */
    @GetMapping
    public CartResDto getCurrentCart(@Valid @ModelAttribute CartInfoReqDto cartInfoReqDto) {
        return CartResDto.of(cartService.getCurrentCart(cartInfoReqDto));
    }

    // 결제 전 요약
    /** {@code GET /api/carts/summary} — 결제 전 요약 (거래 키 + 정상 라인 합계 + 라인 목록). */
    @GetMapping("/summary")
    public CartSummaryResDto getCartSummary(@Valid @ModelAttribute CartInfoReqDto cartInfoReqDto) {
        return CartSummaryResDto.of(cartService.getCartSummary(cartInfoReqDto.getKey()));
    }

    // 장바구니를 바꾸는 API 는 모두 200 + 바뀐 뒤의 장바구니(CartResDto). 실패는 예외 → 전역 처리기 (404 / 409 / 400)
    // *** 현재 임시 데이터 저장소 사용 -> 추후 실제 DB로 이주 계획
    /** {@code POST /api/carts/lines} — 상품 1개 담기. 같은 바코드면 수량 +1. 미등록 404, 판매중지 409. */
    @PostMapping("/lines")
    public CartResDto addToCart(@Valid @RequestBody ProductReqDto productReqDto) {
        return CartResDto.of(cartService.addItem(productReqDto));
    }

    // 다중 상품 일괄 등록 (전부 아니면 전무)
    /** {@code POST /api/carts/lines/batch} — 여러 상품 담기. 빈 목록 400, 한 건이라도 실패하면 아무것도 담지 않음. */
    @PostMapping("/lines/batch")
    public CartResDto addToCart(
            @RequestBody @NotEmpty(message = "담을 상품이 없습니다") List<@Valid ProductReqDto> productReqDtoList) {
        return CartResDto.of(cartService.addItems(productReqDtoList));
    }

    // 상품 수량 업데이트 (절대값)
    /** {@code PATCH /api/carts/lines} — 수량을 절대값으로 변경. qty 누락·1 미만 400, 대상 라인 없음 404. */
    @PatchMapping("/lines")
    public CartResDto updatedProductQty(@Valid @RequestBody UpdateQtyReqDto updateQtyReqDto) {
        return CartResDto.of(cartService.updatedQty(updateQtyReqDto));
    }

    // 지정 취소 — 라인을 지우지 않고 CANCELLED 로 남기므로 DELETE 가 아닌 하위 경로
    /** {@code POST /api/carts/lines/cancel} — 지정 취소. 대상 라인 없음(이미 취소 포함) 404. */
    @PostMapping("/lines/cancel")
    public CartResDto cancelLine(@Valid @RequestBody ProductReqDto productReqDto) {
        return CartResDto.of(cartService.cancelLine(productReqDto));
    }

}
