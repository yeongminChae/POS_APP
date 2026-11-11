package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.sale.Sale;

public interface CartService {

    Cart getCurrentCart(CartInfoReqDto cartInfoReqDto);

    Cart getCurrentCart(CartKey cartKey);

    boolean addItem(ProductReqDto productReqDto);

    boolean updatedQty(ProductReqDto productReqDto);

    boolean cancelLine(ProductReqDto productReqDto);

    void clearCart(Sale sale);

}

