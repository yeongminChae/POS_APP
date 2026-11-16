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

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final ProductService productService;
    private final CartRepository cartRepository;

    @Override
    public Cart getCurrentCart(CartInfoReqDto cartInfoReqDto) {
        CartKey cartKey = cartInfoReqDto.getKey();

        return getCurrentCart(cartKey);
    }

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
    @Override
    // 전부 아니면 전무: 모든 상품을 먼저 조회·검사하고, 하나라도 실패하면 아무것도 담지 않고 예외
    public Cart addItems(List<ProductReqDto> productReqDtoList) {
        List<CartLine> cartLines = new ArrayList<>();
        for (ProductReqDto productReqDto : productReqDtoList)
            cartLines.add(toCartLine(productReqDto));

        for (int i = 0; i < productReqDtoList.size(); i++)
            cartRepository.addOrIncrease(productReqDtoList.get(i).getKey(), cartLines.get(i));

        // 요청마다 키를 받지만 한 장바구니에 담는 API 라 첫 요청의 키로 돌려준다 (빈 목록은 요청 검증에서 400)
        return getCurrentCart(productReqDtoList.get(0).getKey());
    }

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

    @Override
    public Cart cancelLine(ProductReqDto productReqDto) {
        CartKey cartKey = productReqDto.getKey();
        String barcode = productReqDto.getBarcode();

        if (!cartRepository.updateStatusToCancel(cartKey, barcode))
            throw new CartLineNotFoundException(barcode);

        return getCurrentCart(cartKey);
    }

    @Override
    public void clearCart(Sale sale) {
        cartRepository.clearCartStore(sale);
    }

    private CartLine toCartLine(ProductReqDto productReqDto) {
        return CartLine.of(getProduct(productReqDto.getBarcode()), 1);
    }

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
