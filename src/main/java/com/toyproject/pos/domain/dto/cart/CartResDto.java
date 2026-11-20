package com.toyproject.pos.domain.dto.cart;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

// 장바구니 조회 응답. 키 이름은 CartSummaryResDto(SaleResDto.cart)와 같고, 도메인 Cart 의 내부 필드는 내보내지 않는다
/**
 * 장바구니 응답 DTO — {@code GET /api/carts}와 장바구니를 바꾸는 API(담기·일괄 담기·수량 변경·취소)의 200 응답.
 *
 * <p>변경 API 가 "바뀐 뒤의 장바구니"를 통째로 돌려주므로, POS·키오스크 화면은 응답만으로 다시 그릴 수 있다.
 * 도메인 {@link Cart}를 그대로 내보내면 바코드 인덱스·시퀀스 같은 내부 필드가 노출되어 이 DTO 로 감싼다.</p>
 * <p>지금은 {@link CartSummaryResDto}와 필드가 같다 — 장바구니 응답만 따로 바뀔 여지(키오스크 등)를 위해 나눠 두었다.</p>
 */
@Getter
@AllArgsConstructor
@Builder
public class CartResDto {

    private final String strCd;            // 점포 코드
    private final String posNo;            // POS 번호
    private final String tranNo;           // 거래번호
    private final int totalQty;            // 정상 라인 총수량
    private final BigDecimal totalAmount;  // 정상 라인 총금액 (결제할 금액)
    private final List<CartLineDto> lines; // 취소된 라인 포함 (itemStatus 로 구분)

    /** 장바구니 → 응답. 합계 계산은 {@link CartSummary#of}와 같은 규칙(정상 라인 기준)을 쓴다. */
    public static CartResDto of(Cart cart) {
        CartSummary summary = CartSummary.of(cart);
        return CartResDto.builder()
                .strCd(summary.getStrCd())
                .posNo(summary.getPosNo())
                .tranNo(summary.getTranNo())
                .totalQty(summary.getTotalQty())
                .totalAmount(summary.getTotalAmount())
                .lines(summary.getLines().stream().map(CartLineDto::of).toList())
                .build();
    }

}
