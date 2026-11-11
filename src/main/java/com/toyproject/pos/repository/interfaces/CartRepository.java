package com.toyproject.pos.repository.interfaces;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.sale.Sale;

public interface CartRepository {

    Cart getCurrentCart(CartKey cartKey);

    void addOrIncrease(CartKey cartKey, CartLine Item);

    boolean updateItemQty(CartKey cartKey, String barcode, int updatedQty);

    boolean updateStatusToCancel(CartKey cartKey, String barcode);

    void clearCartStore(Sale sale);

}
