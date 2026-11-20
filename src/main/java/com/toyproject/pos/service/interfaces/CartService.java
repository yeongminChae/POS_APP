package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.product.request.UpdateQtyReqDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.sale.Sale;

import java.util.List;

/**
 * 장바구니 서비스 — 결제 전 장바구니(인메모리)를 다룬다. 구현: {@code CartServiceImpl}.
 * 장바구니를 바꾸는 메서드는 모두 바뀐 뒤의 장바구니를 돌려준다 (응답 {@code CartResDto}의 원본).
 */
public interface CartService {

    /** 요청 DTO 의 거래 키로 장바구니 조회. 없으면 빈 장바구니 */
    Cart getCurrentCart(CartInfoReqDto cartInfoReqDto);

    /** 거래 키로 장바구니 조회. 없으면 빈 장바구니 (결제 서비스도 사용) */
    Cart getCurrentCart(CartKey cartKey);

    /** 결제 전 요약: 거래 키 + 정상 라인 합계 + 라인 목록 */
    CartSummary getCartSummary(CartKey cartKey);

    /** 상품 1개 담기. 미등록 404, 판매중지 409. 같은 바코드면 수량 +1 */
    Cart addItem(ProductReqDto productReqDto);

    /** 여러 상품 담기. 전부 검사 후 담음 — 하나라도 실패하면 아무것도 담지 않음 */
    Cart addItems(List<ProductReqDto> productReqDtoList);

    /** 수량을 절대값으로 변경. 대상 라인 없음(취소 포함) 404 */
    Cart updatedQty(UpdateQtyReqDto updateQtyReqDto);

    /** 지정 취소 (CANCELLED 로 남김). 대상 라인 없음(이미 취소 포함) 404 */
    Cart cancelLine(ProductReqDto productReqDto);

    /** 결제 완료 후 해당 판매의 장바구니 삭제 */
    void clearCart(Sale sale);

}
