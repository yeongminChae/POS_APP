package com.toyproject.pos.service.implement;


import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.repository.interfaces.CartRepository;
import com.toyproject.pos.service.interfaces.CartService;
import com.toyproject.pos.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public boolean addItem(ProductReqDto productReqDto) {
        try {
            // 상품 조회
            ProductQueryDto productDto = getProduct(productReqDto.getBarcode());
            // CartLine 객체 생성
            CartLine cartLine = CartLine.of(productDto, 1);

            // 레포지토리에서 카트 모델에 추가 및 수량 증가
            // 현재는 임시 저장소에 저장
            CartKey cartKey = productReqDto.getKey();
            cartRepository.addOrIncrease(cartKey, cartLine);

            // 재고 수량 업데이트
//            productService.updateStock(productDto.getProductId(),
//                    cartRepository.getItemQty(tranNo, barcode));

            return true;

        } catch (Exception ex) {
            ex.toString();

            return false;

        }

    }

    @Override
    public boolean updatedQty(ProductReqDto productReqDto) {
        Integer qty = productReqDto.getQty();
        String barcode = productReqDto.getBarcode();
        CartKey cartKey = productReqDto.getKey();

        if (qty <= 0 || cartRepository.isCanceledItem(cartKey, barcode)) return false;

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

    private ProductQueryDto getProduct(String barcode) {
        ProductQueryDto product = productService.findProduct(barcode);
        if (product == null) {
            throw new IllegalArgumentException("상품을 찾을 수 없습니다. barcode=" + barcode);
        }

        return product;
    }

}
