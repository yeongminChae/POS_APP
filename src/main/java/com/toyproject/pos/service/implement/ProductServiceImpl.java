package com.toyproject.pos.service.implement;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.repository.interfaces.ProductRepository;
import com.toyproject.pos.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 상품 서비스 구현 — 바코드 상품 조회와 재고 반영.
 * 재고 반영은 결제·반품 트랜잭션 안에서 호출되므로, 여기서 예외가 나면 결제·반품 전체가 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    // 상품 조회 : 서비스 + 레포지토리 분리 (추후 확장성 고려)
    /** 바코드로 상품을 조회한다. 없으면 null (판매 가능 여부 판단은 호출한 쪽 — {@code CartServiceImpl}). */
    @Override
    public ProductQueryDto findProduct(String barcode) {
        return productRepository.findByBarcode(barcode);

    }

    /**
     * 재고를 qty 만큼 줄인다 (qty 가 음수면 그만큼 늘어난다 = 반품 복원).
     * 바코드에 해당하는 재고 행이 정확히 1개 바뀌지 않으면 데이터 정합성 오류로 보고 예외(500)를 던진다.
     */
    @Override
    public void decreaseStock(String barcode, int qty) {
        // 재고 부족이어도 막지 않고 음수 허용 (실물 재고 판매 우선)
        if (productRepository.decreaseStockByBarcode(barcode, qty) != 1)
            throw new IllegalStateException("재고 정보가 없습니다. barcode=" + barcode);
    }

}
