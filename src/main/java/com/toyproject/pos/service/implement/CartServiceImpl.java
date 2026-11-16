package com.toyproject.pos.service.implement;


import com.toyproject.pos.common.exception.ProductDiscontinuedException;
import com.toyproject.pos.common.exception.ProductNotFoundException;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.dto.product.request.UpdateQtyReqDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
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

    // 상품 데이터 조회 + 조회 해온 결과 cart 모델에 담는 함수
    @Override
    // 미등록·판매중지는 예외(ProductNotFoundException / ProductDiscontinuedException)로 알리고, 전역 처리기가 404 / 409로 응답
    // 반환값은 A3(응답 형식 통일)에서 Cart 로 바뀔 때까지 항상 true
    public boolean addItem(ProductReqDto productReqDto) {
        // 상품 조회 + CartLine 생성
        CartLine cartLine = toCartLine(productReqDto);

        // 레포지토리에서 카트 모델에 추가 및 수량 증가 (현재는 인메모리 저장소)
        // 재고는 담을 때가 아니라 결제·반품 시점에 반영 (CheckoutServiceImpl)
        cartRepository.addOrIncrease(productReqDto.getKey(), cartLine);

        return true;
    }

    // 다중 상품 일괄 등록
    @Override
    // 전부 아니면 전무: 모든 상품을 먼저 조회·검사하고, 하나라도 실패하면 아무것도 담지 않고 예외
    public boolean addItems(List<ProductReqDto> productReqDtoList) {
        List<CartLine> cartLines = new ArrayList<>();
        for (ProductReqDto productReqDto : productReqDtoList)
            cartLines.add(toCartLine(productReqDto));

        for (int i = 0; i < productReqDtoList.size(); i++)
            cartRepository.addOrIncrease(productReqDtoList.get(i).getKey(), cartLines.get(i));

        return true;
    }

    @Override
    public boolean updatedQty(UpdateQtyReqDto updateQtyReqDto) {
        int qty = updateQtyReqDto.getQty();
        String barcode = updateQtyReqDto.getBarcode();
        CartKey cartKey = updateQtyReqDto.getKey();

        // 요청 검증(@Min(1))을 거치지 않은 호출 대비
        if (qty <= 0) return false;

        return cartRepository.updateItemQty(cartKey, barcode, qty);

    }

    @Override
    public boolean cancelLine(ProductReqDto productReqDto) {
        CartKey cartKey = productReqDto.getKey();
        String barcode = productReqDto.getBarcode();

        return cartRepository.updateStatusToCancel(cartKey, barcode);
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
