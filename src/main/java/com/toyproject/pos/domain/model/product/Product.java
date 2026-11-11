package com.toyproject.pos.domain.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    public String productId;
    public String barcode;
    public String name;
    public BigDecimal price;
    public String productStatus;
    public String expiryDate;
    public boolean isAdultProduct;

}
