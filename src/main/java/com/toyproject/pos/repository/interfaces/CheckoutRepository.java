package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.Sale;

public interface CheckoutRepository {

    Long save(Sale sale);

//    Sale getTransaction(CartKey cartKey);

    Sale getTransaction(CartKey cartKey, Cart cart, Payment payment);

    Cart getCart(CartKey cartKey);

    Payment getPayment(CartKey cartKey);

}
