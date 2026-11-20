package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;

/**
 * 상품 서비스 — 상품 조회와 재고 반영. 구현: {@code ProductServiceImpl}.
 */
public interface ProductService {
    /** 바코드로 상품 조회. 없으면 null */
    ProductQueryDto findProduct(String barcode);

    // 재고 차감 (qty 가 음수면 복원). 재고 행이 없으면 IllegalStateException
    /** 결제(차감)·반품(복원) 시 바코드 기준으로 재고를 반영한다. 재고 부족이어도 막지 않는다 (음수 재고 허용). */
    void decreaseStock(String barcode, int qty);

}
