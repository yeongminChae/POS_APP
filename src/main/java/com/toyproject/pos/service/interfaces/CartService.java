package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.product.request.UpdateQtyReqDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.sale.Sale;

import java.util.List;

public interface CartService {

    Cart getCurrentCart(CartInfoReqDto cartInfoReqDto);

    Cart getCurrentCart(CartKey cartKey);

    Cart addItem(ProductReqDto productReqDto);

    Cart addItems(List<ProductReqDto> productReqDtoList);

    Cart updatedQty(UpdateQtyReqDto updateQtyReqDto);

    Cart cancelLine(ProductReqDto productReqDto);

    void clearCart(Sale sale);

}

