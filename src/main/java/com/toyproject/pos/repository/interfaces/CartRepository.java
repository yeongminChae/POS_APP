package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.sale.Sale;

/**
 * 장바구니 저장소 — 거래 키별 장바구니 보관. 지금 구현은 인메모리({@code CartRepositoryImpl}).
 * DB 로 옮길 때 이 인터페이스를 유지하면 서비스는 바꾸지 않아도 된다.
 */
public interface CartRepository {

    /** 거래 키의 장바구니. 없으면 빈 장바구니를 만들어 돌려준다 */
    Cart getCurrentCart(CartKey cartKey);

    /** 라인 담기 (같은 바코드 정상 라인이 있으면 수량 합산) */
    void addOrIncrease(CartKey cartKey, CartLine Item);

    /** 수량을 절대값으로 변경. 대상 라인이 없으면 false */
    boolean updateItemQty(CartKey cartKey, String barcode, int updatedQty);

    /** 라인을 취소 상태로 변경. 대상 라인이 없으면 false */
    boolean updateStatusToCancel(CartKey cartKey, String barcode);

    /** 결제된 판매의 장바구니 삭제 */
    void clearCartStore(Sale sale);

}
