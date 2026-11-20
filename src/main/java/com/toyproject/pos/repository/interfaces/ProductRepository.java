package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;

/**
 * 상품 저장소 — 상품 조회·재고 반영 (DB). 구현: {@code ProductRepositoryImpl}.
 */
public interface ProductRepository {
    /** 바코드로 상품 조회. 없으면 null */
    ProductQueryDto findByBarcode(String barcode);

    /** 재고를 qty 만큼 차감(음수면 복원)하고 바뀐 행 수를 반환 */
    int decreaseStockByBarcode(String barcode, int qty);

}
