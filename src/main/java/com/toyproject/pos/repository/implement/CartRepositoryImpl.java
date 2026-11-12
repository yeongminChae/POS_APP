package com.toyproject.pos.repository.implement;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.repository.interfaces.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    // 키: 점포·POS·거래번호 (CartKey)
    private final Map<CartKey, Cart> cartStore = new LinkedHashMap<>(); // 순서 고려 하기 위해 LinkedHashMap 사용

    // 현재 저장 된, 카트 모델 조회
    @Override
    public Cart getCurrentCart(CartKey cartKey) {
        // cartStore 에서 있으면 기존 카트 모델 반환, 없으면 LinkedHashMap 생성 후 반환
        return cartStore.computeIfAbsent(cartKey, key ->
                Cart.of(cartKey, new LinkedHashMap<>())
        );

    }

    // 카트 모델 + 카트 아이디 가지고 임시 저장소 맵 생성 및 추가
    @Override
    public void addOrIncrease(CartKey cartKey, CartLine item) {
        cartStore.compute(cartKey, (key, cart) -> {
            if (cart == null)
                cart = Cart.of(cartKey, new LinkedHashMap<>());
            cart.addOrIncrease(item);

            return cart;
        });

    }

    @Override
    public boolean updateItemQty(CartKey cartKey, String barcode, int updatedQty) {
        return getCurrentCart(cartKey).updatedItemQty(barcode, updatedQty);

    }

    @Override
    public boolean updateStatusToCancel(CartKey cartKey, String barcode) {
        return getCurrentCart(cartKey).cancelCartLine(barcode);

    }

    @Override
    public void clearCartStore(Sale sale) {
        cartStore.remove(CartKey.of(sale.getStoreCd(), sale.getPosNo(), sale.getTranNo()));

    }

}