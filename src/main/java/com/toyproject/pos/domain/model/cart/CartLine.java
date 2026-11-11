package com.toyproject.pos.domain.model.cart;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true) // ← toBuilder: 복사 빌더
public class CartLine {
    private final String productId;
    private final String lineId;
    private final String barcode;
    private final String name;
    private final BigDecimal unitPrice;
    private final int qty;
    private final BigDecimal amount;
    private final ItemStatus itemStatus;

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
     */
    public CartLine withLineId(String lineId) {
        return this.toBuilder()
                .lineId(lineId)
                .build();
    }

    public CartLine updatedOf(int qty) {
        return this.toBuilder()
                .qty(qty)
                .amount(getTotalAmount(qty, unitPrice))
                .build();
    }

    public CartLine cancelledOf() {
        return this.toBuilder()
                .amount(BigDecimal.ZERO)
                .itemStatus(ItemStatus.CANCELLED)
                .build();
    }

    public CartLine refundOf() {
        int negativeQty = Math.negateExact(this.qty);
        return this.toBuilder()
                .qty(negativeQty)
                .amount(getTotalAmount(negativeQty, this.unitPrice))
                .itemStatus(ItemStatus.REFUNDED)
                .build();
    }

    public CartLine increaseOf(CartLine newLine) {
        int newQty = qty + newLine.getQty();
        return updatedOf(newQty);
    }

    private static BigDecimal getTotalAmount(int qty, BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(qty));
    }

}
