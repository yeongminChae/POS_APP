package com.toyproject.pos.domain.model.cart;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 장바구니 라인 — 장바구니에 담긴 상품 한 줄 (영수증의 한 줄, DB {@code SALE_LINES}의 한 행).
 *
 * <p><b>불변 객체</b>다. 수량 변경·취소·반품은 기존 객체를 고치지 않고 {@code xxxOf()}로 새 객체를 만들어
 * {@link Cart}의 맵에 다시 넣는다. 그래서 {@code Cart}는 라인을 바꿀 때 항상 맵에 결과를 저장한다.</p>
 *
 * <h2>상태별 수량·금액 규칙</h2>
 * <ul>
 *   <li>NORMAL(정상): 수량 ≥ 1, 금액 = 단가 × 수량. 결제 합계와 재고 차감 대상</li>
 *   <li>CANCELLED(지정 취소): 수량은 그대로 두고 금액만 0. 합계·재고 반영에서 제외 (영수증에는 남김)</li>
 *   <li>REFUNDED(반품): 수량·금액 모두 음수. 저장 후 재고 반영 시 음수 차감 = 재고 복원</li>
 *   <li>EMPTY: 라인이 없음을 나타내는 {@link #EMPTY} 전용 (null 대신 사용)</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
@Builder(toBuilder = true) // ← toBuilder: 복사 빌더
public class CartLine {
    private final String productId;      // 상품 ID (PRODUCT.PRODUCT_ID). 응답 DTO 로는 내보내지 않는다
    private final String lineId;         // 라인 번호 "0001"… — 객체 안에는 비워 두고, Cart 가 밖으로 내보낼 때 채운다
    private final String barcode;        // 상품 바코드 (재고 반영의 기준 키)
    private final String name;           // 상품명 (담을 때의 값을 그대로 보관)
    private final BigDecimal unitPrice;  // 단가 (담을 때의 판매가)
    private final int qty;               // 수량 (반품 라인은 음수)
    private final BigDecimal amount;     // 금액 = 단가 × 수량 (취소 라인은 0, 반품 라인은 음수)
    private final ItemStatus itemStatus; // 라인 상태

    /** 라인이 없을 때 돌려주는 빈 라인 (null 대신). 수량 0·금액 0·상태 EMPTY. {@link Cart#getCartLine}이 사용한다. */
    public static final CartLine EMPTY = CartLine.builder()
            .productId("")
            .lineId(null)
            .barcode("")
            .name("")
            .unitPrice(BigDecimal.ZERO)
            .qty(0)
            .amount(BigDecimal.ZERO)
            .itemStatus(ItemStatus.EMPTY)
            .build();

    /**
     * 상품 스캔으로 새 정상 라인을 만든다. 상품 정보(이름·단가)는 담는 시점의 DB 값을 복사해 둔다.
     * lineId 는 비워 두고 {@link Cart#addOrIncrease}가 바코드 인덱스로 정한다.
     *
     * @param product 바코드로 조회한 상품 (판매 가능 여부는 서비스에서 이미 검사함)
     * @param qty     수량 (담기는 항상 1)
     */
    public static CartLine of(ProductQueryDto product, int qty) {
        BigDecimal price = product.getPrice();
        return CartLine.builder()
                .productId(product.getProductId())
                .lineId(null) // 생성 시엔 항상 null
                .barcode(product.getBarcode())
                .name(product.getName())
                .unitPrice(price)
                .qty(qty)
                .amount(getTotalAmount(qty, price))
                .itemStatus(ItemStatus.NORMAL)
                .build();
    }

    /**
     * Cart가 외부로 내보낼 때 라인아이디를 주입하는 용도
     * (라인 객체는 lineId 를 모르고, Cart 의 맵 키가 lineId 다 — {@link Cart#getLinesList()}).
     */
    public CartLine withLineId(String lineId) {
        return this.toBuilder()
                .lineId(lineId)
                .build();
    }

    /** 수량을 주어진 값(절대값)으로 바꾸고 금액을 다시 계산한 새 라인. 수량 변경 API와 재스캔(수량 +1)이 쓴다. */
    public CartLine updatedOf(int qty) {
        return this.toBuilder()
                .qty(qty)
                .amount(getTotalAmount(qty, unitPrice))
                .build();
    }

    /** 지정 취소된 새 라인: 상태 CANCELLED, 금액 0. 수량은 기록용으로 그대로 둔다. */
    public CartLine cancelledOf() {
        return this.toBuilder()
                .amount(BigDecimal.ZERO)
                .itemStatus(ItemStatus.CANCELLED)
                .build();
    }

    /**
     * 반품된 새 라인: 상태 REFUNDED, 수량·금액을 음수로 바꾼다.
     * 음수로 저장하면 판매 합계 집계와 재고 반영(음수 차감 = 복원)을 같은 계산으로 처리할 수 있다.
     */
    public CartLine refundOf() {
        int negativeQty = Math.negateExact(this.qty);
        return this.toBuilder()
                .qty(negativeQty)
                .amount(getTotalAmount(negativeQty, this.unitPrice))
                .itemStatus(ItemStatus.REFUNDED)
                .build();
    }

    /** 같은 바코드를 다시 스캔했을 때: 기존 수량에 새 라인 수량을 더한 새 라인 ({@link Cart#addOrIncrease}의 merge 함수). */
    public CartLine increaseOf(CartLine newLine) {
        int newQty = qty + newLine.getQty();
        return updatedOf(newQty);
    }

    /** 금액 = 단가 × 수량 */
    private static BigDecimal getTotalAmount(int qty, BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(qty));
    }

}
