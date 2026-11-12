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
import java.util.concurrent.ConcurrentHashMap;

@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    // 키: 점포·POS·거래번호 (CartKey)
    // POS 여러 대의 요청이 동시에 들어오므로 동시 접근에 안전한 맵 사용 (순서는 쓰는 곳 없음)
    private final Map<CartKey, Cart> cartStore = new ConcurrentHashMap<>();

    // 현재 저장 된, 카트 모델 조회
    @Override
    public Cart getCurrentCart(CartKey cartKey) {
        // cartStore 에서 있으면 기존 카트 모델 반환, 없으면 새 카트 생성 후 반환
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