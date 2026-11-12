package com.toyproject.pos.domain.dto.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQueryDto {
    private String productId;
    private String barcode;
    private String name;
    private BigDecimal price;
    private String brandId;
    private String categoryId;
    private String productStatus; // normal / suspend_order(발주중단) / discontinued(판매중지), NULL 가능

    public boolean isDiscontinued() {
        return "discontinued".equals(productStatus);
    }

}
