package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;

public interface ProductService {
    ProductQueryDto findProduct(String barcode);

    int updateStock(String productId, int updateQty);

}
