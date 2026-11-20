package com.toyproject.pos.service.implement;


import com.toyproject.pos.common.exception.CartLineNotFoundException;
import com.toyproject.pos.common.exception.ProductDiscontinuedException;
import com.toyproject.pos.common.exception.ProductNotFoundException;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.product.request.UpdateQtyReqDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.repository.interfaces.CartRepository;
import com.toyproject.pos.service.interfaces.CartService;
import com.toyproject.pos.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 장바구니 서비스 구현 — 상품 담기·수량 변경·취소·조회 ({@code CartController}가 호출, 결제 서비스도 사용).
 *
 * <ul>
 *   <li>장바구니는 DB 가 아니라 인메모리 저장소({@code CartRepositoryImpl})에 있다. 상품 정보만 DB 에서 읽는다.</li>
 *   <li>재고는 담을 때 건드리지 않는다 — 결제·반품 시점에 {@code CheckoutServiceImpl}이 반영한다.</li>
 *   <li>트랜잭션이 없다 (DB 에 쓰지 않음).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final ProductService productService;   // 바코드로 상품 조회 (DB)
    private final CartRepository cartRepository;   // 인메모리 장바구니 저장소

    /** 요청 DTO 의 거래 키로 장바구니를 조회한다 (없으면 빈 장바구니가 만들어진다). */
    @Override
    public Cart getCurrentCart(CartInfoReqDto cartInfoReqDto) {
        CartKey cartKey = cartInfoReqDto.getKey();

        return getCurrentCart(cartKey);
    }

    /** 거래 키로 장바구니를 조회한다 (없으면 빈 장바구니가 만들어진다). */
    @Override
    public Cart getCurrentCart(CartKey cartKey) {
        return cartRepository.getCurrentCart(cartKey);
    }

    // 결제 전 요약 (합계는 정상 라인 기준)
    @Override
    public CartSummary getCartSummary(CartKey cartKey) {
        return CartSummary.of(getCurrentCart(cartKey));
    }

    // 상품 데이터 조회 + 조회 해온 결과 cart 모델에 담는 함수
    /**
     * 상품 1개 담기.
     * <ol>
     *   <li>바코드로 상품 조회 → 미등록 404 PRODUCT_NOT_FOUND, 판매중지 409 PRODUCT_DISCONTINUED</li>
     *   <li>수량 1짜리 라인 생성</li>
     *   <li>장바구니에 추가 — 같은 바코드의 정상 라인이 있으면 수량 +1, 없으면 새 라인</li>
     * </ol>
     */
    @Override
    // 미등록·판매중지는 예외(ProductNotFoundException / ProductDiscontinuedException)로 알리고, 전역 처리기가 404 / 409로 응답
    // 장바구니를 바꾸는 메서드는 모두 바뀐 뒤의 장바구니를 돌려준다
    public Cart addItem(ProductReqDto productReqDto) {
        // 상품 조회 + CartLine 생성
        CartLine cartLine = toCartLine(productReqDto);

        // 레포지토리에서 카트 모델에 추가 및 수량 증가 (현재는 인메모리 저장소)
        // 재고는 담을 때가 아니라 결제·반품 시점에 반영 (CheckoutServiceImpl)
        cartRepository.addOrIncrease(productReqDto.getKey(), cartLine);

        return getCurrentCart(productReqDto.getKey());
    }

    // 다중 상품 일괄 등록
    /**
     * 여러 상품을 한 번에 담기. 1단계에서 전부 조회·검사하고, 모두 통과했을 때만 2단계에서 담는다.
     * 그래서 중간에 미등록·판매중지 상품이 있으면 장바구니는 전혀 바뀌지 않는다.
     */
    @Override
    // 전부 아니면 전무: 모든 상품을 먼저 조회·검사하고, 하나라도 실패하면 아무것도 담지 않고 예외
    public Cart addItems(List<ProductReqDto> productReqDtoList) {
        // 1단계: 검사 + 라인 생성 (여기서 예외가 나면 아무것도 담기지 않음)
        List<CartLine> cartLines = new ArrayList<>();
        for (ProductReqDto productReqDto : productReqDtoList)
            cartLines.add(toCartLine(productReqDto));

        // 2단계: 담기
        for (int i = 0; i < productReqDtoList.size(); i++)
            cartRepository.addOrIncrease(productReqDtoList.get(i).getKey(), cartLines.get(i));

        // 요청마다 키를 받지만 한 장바구니에 담는 API 라 첫 요청의 키로 돌려준다 (빈 목록은 요청 검증에서 400)
        return getCurrentCart(productReqDtoList.get(0).getKey());
    }

    /**
     * 수량 변경 — 바코드의 정상 라인 수량을 주어진 값(절대값)으로 바꾼다.
     * 대상 라인이 없거나 이미 취소된 라인이면 404 CART_LINE_NOT_FOUND.
     */
    @Override
    public Cart updatedQty(UpdateQtyReqDto updateQtyReqDto) {
        int qty = updateQtyReqDto.getQty();
        String barcode = updateQtyReqDto.getBarcode();
        CartKey cartKey = updateQtyReqDto.getKey();

        // 요청 검증(@Min(1))을 거치지 않은 잘못된 호출
        if (qty <= 0) throw new IllegalArgumentException("수량은 1 이상이어야 합니다. qty=" + qty);

        if (!cartRepository.updateItemQty(cartKey, barcode, qty))
            throw new CartLineNotFoundException(barcode);

        return getCurrentCart(cartKey);

    }

    /**
     * 지정 취소 — 바코드의 정상 라인을 CANCELLED 로 바꾼다 (라인은 지우지 않고 금액 0으로 남김).
     * 대상 라인이 없거나 이미 취소된 라인이면 404 CART_LINE_NOT_FOUND. 취소 후 같은 바코드를 담으면 새 라인이 생긴다.
     */
    @Override
    public Cart cancelLine(ProductReqDto productReqDto) {
        CartKey cartKey = productReqDto.getKey();
        String barcode = productReqDto.getBarcode();

        if (!cartRepository.updateStatusToCancel(cartKey, barcode))
            throw new CartLineNotFoundException(barcode);

        return getCurrentCart(cartKey);
    }

    /** 결제가 커밋된 뒤 해당 판매의 장바구니를 저장소에서 지운다 ({@code CheckoutServiceImpl}의 afterCommit). */
    @Override
    public void clearCart(Sale sale) {
        cartRepository.clearCartStore(sale);
    }

    /** 요청의 바코드로 상품을 조회·검사해 수량 1짜리 장바구니 라인을 만든다. */
    private CartLine toCartLine(ProductReqDto productReqDto) {
        return CartLine.of(getProduct(productReqDto.getBarcode()), 1);
    }

    /**
     * 판매 가능한 상품인지 확인하고 돌려준다.
     * 미등록 → {@link ProductNotFoundException}(404), 판매중지 → {@link ProductDiscontinuedException}(409).
     * 성인 전용 상품도 지금은 그대로 담긴다 (성인 인증은 보류 항목 B12c).
     */
    private ProductQueryDto getProduct(String barcode) {
        ProductQueryDto product = productService.findProduct(barcode);
        if (product == null) {
            throw new ProductNotFoundException(barcode);
        }
        // 판매중지만 막는다 (발주중단은 남은 재고 판매용이라 허용, 상태 NULL 도 허용)
        if (product.isDiscontinued()) {
            throw new ProductDiscontinuedException(barcode);
        }

        return product;
    }

}
