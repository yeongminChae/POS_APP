package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.Sale;

/**
 * 판매 저장소 — 판매의 저장·조회·반품 표시 (DB). 구현: {@code CheckoutRepositoryImpl}.
 * 조회는 세 부분(장바구니·결제·판매 머리)을 따로 읽어 서비스가 조립한다 ({@code CheckoutServiceImpl.loadTransaction}).
 */
public interface CheckoutRepository {

    /** 판매 저장 (SALES·SALE_LINES·PAYMENTS·CASH_PAYMENTS·RECEIPT_JOURNAL). 생성된 SALE_ID 반환 */
    Long save(Sale sale);

    // 원거래를 반품 상태로 변경. 정상 거래가 아니면(이미 반품됨) false
    boolean markRefunded(Long saleId);

    /** SALES 행 + 복원한 장바구니 요약·결제 → 판매 */
    Sale getTransaction(CartKey cartKey, CartSummary cartSummary, Payment payment);

    /** 거래 키로 저장된 판매가 있는지 */
    boolean existsTransaction(CartKey cartKey);

    /** SALE_LINES → 장바구니 복원 */
    Cart getCart(CartKey cartKey);

    /** PAYMENTS(+CASH_PAYMENTS) → 결제 복원 */
    Payment getPayment(CartKey cartKey);

}
