package com.toyproject.pos.repository.implement;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.persistence.mapper.ProductMapper;
import com.toyproject.pos.repository.interfaces.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    // 상품 조회 : 서비스 + 레포지토리 분리 (추후 확장성 고려)
    @Override
    public ProductQueryDto findByBarcode(String barcode) {
        return productMapper.findByBarcode(barcode);

    }

    @Override
    public int decreaseStockByBarcode(String barcode, int qty) {
        return productMapper.decreaseStockByBarcode(barcode, qty);
    }

}
