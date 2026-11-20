package com.toyproject.pos.domain.model.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 상품 도메인 모델 (자리만 있음).
 *
 * <p>⚠ 현재 코드 어디에서도 사용하지 않는다. 상품 조회는 {@code ProductQueryDto}(바코드 조회 결과)를 쓴다.
 * 키오스크 메뉴 API 등 상품 기능을 만들 때 쓸지, 삭제할지 정한다.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    public String productId;       // 상품 ID
    public String barcode;         // 바코드
    public String name;            // 상품명
    public BigDecimal price;       // 판매가
    public String productStatus;   // 상품 상태 (normal / suspend_order / discontinued / NULL)
    public String expiryDate;      // 유통기한
    public boolean isAdultProduct; // 성인 전용 여부

}
