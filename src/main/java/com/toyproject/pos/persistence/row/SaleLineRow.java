package com.toyproject.pos.persistence.row;


import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * {@code SALE_LINES} 테이블 1행 — 판매 라인 (장바구니 라인의 저장 형태).
 * 취소·반품 라인도 모두 저장된다. 상품 ID 컬럼은 없고 바코드·이름·단가를 판매 시점 값으로 복사해 둔다.
 */
@Getter
@Builder
@AllArgsConstructor
public class SaleLineRow {
    private final Long saleLineId;       // AUTO
    private final Long saleId;           // FK
    private final String lineNo;        // 1,2,3...
    private final String barcode;        // 바코드 (반품 시 재고 복원 기준)
    private final String name;           // 상품명 (판매 시점)
    private final BigDecimal unitPrice;  // 단가 (판매 시점)
    private final Integer qty;           // 수량 (반품은 음수)
    private final BigDecimal amount;     // 금액 (취소 0, 반품 음수)
    private final ItemStatus itemStatus;     // "NORMAL","CANCELLED", 반품 거래는 "REFUNDED"

    /** 장바구니 라인 → 행. 라인 번호는 장바구니의 lineId("0001"…)를 그대로 쓴다. */
    public static SaleLineRow of(CartLine line, Long saleId) {
        return SaleLineRow.builder()
                .saleLineId(null)
                .saleId(saleId)
                .lineNo(line.getLineId())
                .barcode(line.getBarcode())
                .name(line.getName())
                .unitPrice(line.getUnitPrice())
                .qty(line.getQty())
                .amount(line.getAmount())
                .itemStatus(line.getItemStatus())
                .build();
    }

}
