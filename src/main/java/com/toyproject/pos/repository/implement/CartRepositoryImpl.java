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

/**
 * 장바구니 저장소 — <b>인메모리 임시 구현</b> (추후 DB 이전 예정).
 *
 * <ul>
 *   <li>스프링 싱글톤 빈 안의 맵이라 서버를 재시작하면 결제 전 장바구니는 모두 사라진다.</li>
 *   <li>서버 한 대 기준이다. 서버를 여러 대로 늘리면 장바구니가 서버마다 따로 생기므로 DB·외부 저장소로 옮겨야 한다.</li>
 *   <li>담기는 {@code compute}로 원자적으로 처리해 서로 다른 거래 키의 동시 요청이 안전하다 ({@code ConcurrentPosIntegrationTest}).
 *       수량 변경·취소는 장바구니를 꺼낸 뒤 바꾸므로, 같은 거래 키에 동시 요청이 오면 안전하지 않다 — 한 거래는 한 POS 가 다룬다는 전제.</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class CartRepositoryImpl implements CartRepository {

    // 키: 점포·POS·거래번호 (CartKey)
    // POS 여러 대의 요청이 동시에 들어오므로 동시 접근에 안전한 맵 사용 (순서는 쓰는 곳 없음)
    private final Map<CartKey, Cart> cartStore = new ConcurrentHashMap<>();

    // 현재 저장 된, 카트 모델 조회
    /**
     * 거래 키의 장바구니를 돌려준다. 없으면 빈 장바구니를 만들어 저장한 뒤 돌려준다
     * (그래서 "거래 시작" API 가 따로 없고, 첫 조회·담기가 곧 거래 시작이다).
     */
    @Override
    public Cart getCurrentCart(CartKey cartKey) {
        // cartStore 에서 있으면 기존 카트 모델 반환, 없으면 새 카트 생성 후 반환
        return cartStore.computeIfAbsent(cartKey, key ->
                Cart.of(cartKey, new LinkedHashMap<>())
        );

    }

    // 카트 모델 + 카트 아이디 가지고 임시 저장소 맵 생성 및 추가
    /** 거래 키의 장바구니에 라인을 담는다 (없으면 장바구니 생성). 같은 바코드 정상 라인이 있으면 수량을 더한다. */
    @Override
    public void addOrIncrease(CartKey cartKey, CartLine item) {
        cartStore.compute(cartKey, (key, cart) -> {
            if (cart == null)
                cart = Cart.of(cartKey, new LinkedHashMap<>());
            cart.addOrIncrease(item);

            return cart;
        });

    }

    /** 바코드 정상 라인의 수량을 절대값으로 바꾼다. 대상 라인이 없으면 false. */
    @Override
    public boolean updateItemQty(CartKey cartKey, String barcode, int updatedQty) {
        return getCurrentCart(cartKey).updatedItemQty(barcode, updatedQty);

    }

    /** 바코드 정상 라인을 취소 상태로 바꾼다. 대상 라인이 없으면(이미 취소 포함) false. */
    @Override
    public boolean updateStatusToCancel(CartKey cartKey, String barcode) {
        return getCurrentCart(cartKey).cancelCartLine(barcode);

    }

    /** 결제된 판매의 거래 키로 장바구니를 지운다 (결제 커밋 후 호출). */
    @Override
    public void clearCartStore(Sale sale) {
        cartStore.remove(CartKey.of(sale.getStoreCd(), sale.getPosNo(), sale.getTranNo()));

    }

}
