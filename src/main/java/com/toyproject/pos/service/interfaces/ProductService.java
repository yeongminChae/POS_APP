package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;

public interface ProductService {
    ProductQueryDto findProduct(String barcode);

    // 재고 차감 (qty 가 음수면 복원). 재고 행이 없으면 IllegalStateException
    void decreaseStock(String barcode, int qty);

}
