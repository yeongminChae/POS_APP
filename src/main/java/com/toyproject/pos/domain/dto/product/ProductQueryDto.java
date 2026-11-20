package com.toyproject.pos.domain.dto.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 바코드 상품 조회 결과 — {@code ProductMapper.findByBarcode}가 {@code PRODUCT} 행을 이 객체로 채운다.
 * 장바구니 라인을 만들 때({@code CartLine.of}) 이름·단가를 복사해 간다. API 응답으로는 나가지 않는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductQueryDto {
    private String productId;   // 상품 ID
    private String barcode;     // 바코드
    private String name;        // 상품명
    private BigDecimal price;   // 판매가
    private String brandId;     // 브랜드 ID (없을 수 있음)
    private String categoryId;  // 카테고리 ID
    private String productStatus; // normal / suspend_order(발주중단) / discontinued(판매중지), NULL 가능

    /**
     * 판매중지 상품인지. 판매중지만 담기를 막는다 (409 PRODUCT_DISCONTINUED).
     * 발주중단(suspend_order)은 남은 재고를 팔아야 하므로 허용하고, 상태가 NULL 인 상품도 허용한다.
     */
    public boolean isDiscontinued() {
        return "discontinued".equals(productStatus);
    }

}
