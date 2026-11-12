package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.Sale;

public interface CheckoutRepository {

    Long save(Sale sale);

    // 원거래를 반품 상태로 변경. 정상 거래가 아니면(이미 반품됨) false
    boolean markRefunded(Long saleId);

    Sale getTransaction(CartKey cartKey, CartSummary cartSummary, Payment payment);

    boolean existsTransaction(CartKey cartKey);

    Cart getCart(CartKey cartKey);

    Payment getPayment(CartKey cartKey);

}
