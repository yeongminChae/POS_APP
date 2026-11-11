package com.toyproject.pos.persistence.row;


import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class SaleLineRow {
    private final Long saleLineId;       // AUTO
    private final Long saleId;           // FK
    private final String lineNo;        // 1,2,3...
    private final String barcode;
    private final String name;
    private final BigDecimal unitPrice;
    private final Integer qty;
    private final BigDecimal amount;
    private final ItemStatus itemStatus;     // "NORMAL","CANCELLED"

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

