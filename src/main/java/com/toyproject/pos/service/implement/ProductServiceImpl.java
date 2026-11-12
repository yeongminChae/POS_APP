package com.toyproject.pos.service.implement;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.repository.interfaces.ProductRepository;
import com.toyproject.pos.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    // 상품 조회 : 서비스 + 레포지토리 분리 (추후 확장성 고려)
    @Override
    public ProductQueryDto findProduct(String barcode) {
        return productRepository.findByBarcode(barcode);

    }

    @Override
    public void decreaseStock(String barcode, int qty) {
        // 재고 부족이어도 막지 않고 음수 허용 (실물 재고 판매 우선)
        if (productRepository.decreaseStockByBarcode(barcode, qty) != 1)
            throw new IllegalStateException("재고 정보가 없습니다. barcode=" + barcode);
    }

}
