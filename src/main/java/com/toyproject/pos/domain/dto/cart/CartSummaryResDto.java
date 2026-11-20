package com.toyproject.pos.domain.dto.cart;


import com.toyproject.pos.domain.model.cart.CartSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// 장바구니 요약 (결제 요약 API, SaleResDto.cart)
/**
 * 장바구니 요약 응답 DTO — 두 곳에서 쓴다.
 * <ul>
 *   <li>{@code GET /api/carts/summary}: 결제 전 요약</li>
 *   <li>{@code SaleResDto.cart}: 결제·조회·반품 응답 안의 장바구니 스냅샷 (반품 거래면 합계·라인이 음수)</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
@Builder
public class CartSummaryResDto {

    private final String strCd;            // 점포 코드
    private final String posNo;            // POS 번호
    private final String tranNo;           // 거래번호
    private final int totalQty;            // 총수량
    private final BigDecimal totalAmount;  // 총금액
    private final List<CartLineDto> lines; // 라인 목록 (취소·반품 라인 포함)

    /** 도메인 요약 → 응답 */
    public static CartSummaryResDto of(CartSummary summary) {
        return CartSummaryResDto.builder()
                .strCd(summary.getStrCd())
                .posNo(summary.getPosNo())
                .tranNo(summary.getTranNo())
                .totalQty(summary.getTotalQty())
                .totalAmount(summary.getTotalAmount())
                .lines(getCartLineDtoList(summary))
                .build();
    }

    /** 도메인 라인 목록 → 응답 라인 목록 (수정 불가) */
    private static List<CartLineDto> getCartLineDtoList(CartSummary summary) {
        return summary.getLines().stream()
                .map(cartLine -> CartLineDto.of(cartLine))
                .collect(Collectors.toUnmodifiableList());

    }


}
