package com.toyproject.pos.persistence.mapper;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 상품·재고 MyBatis 매퍼. SQL 은 {@code resources/mapper/ProductMapper.xml}에 있다.
 */
@Mapper
public interface ProductMapper {
    /** 바코드로 상품 조회 (PRODUCT). 없으면 null */
    ProductQueryDto findByBarcode(@Param("barcode") String barcode);

    /**
     * 재고 차감: {@code UPDATE INVENTORY SET STOCK = STOCK - qty} (바코드 → PRODUCT_ID 로 찾음).
     * 하한 검사가 없어 재고가 음수가 될 수 있다 (정책). 바뀐 행 수를 반환 — 0이면 재고 행이 없는 것.
     */
    int decreaseStockByBarcode(@Param("barcode") String barcode,
                               @Param("qty") int qty);

}
