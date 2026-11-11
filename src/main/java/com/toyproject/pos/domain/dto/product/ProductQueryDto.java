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

}
