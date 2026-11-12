package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;

public interface ProductRepository {
    ProductQueryDto findByBarcode(String barcode);

    int decreaseStockByBarcode(String barcode, int qty);

}
