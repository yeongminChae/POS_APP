package com.toyproject.pos.repository.implement;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.persistence.mapper.ProductMapper;
import com.toyproject.pos.repository.interfaces.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 상품 저장소 — {@code PRODUCT}·{@code INVENTORY} 테이블 접근을 MyBatis 매퍼({@code ProductMapper.xml})에 위임한다.
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    // 상품 조회 : 서비스 + 레포지토리 분리 (추후 확장성 고려)
    /** 바코드로 상품 조회 (없으면 null) */
    @Override
    public ProductQueryDto findByBarcode(String barcode) {
        return productMapper.findByBarcode(barcode);

    }

    /** 바코드 상품의 재고를 qty 만큼 줄인다 (음수면 늘림). 바뀐 행 수를 돌려준다 (정상이면 1) */
    @Override
    public int decreaseStockByBarcode(String barcode, int qty) {
        return productMapper.decreaseStockByBarcode(barcode, qty);
    }

}
