package com.toyproject.pos.service.implement;

import com.toyproject.pos.common.exception.ProductDiscontinuedException;
import com.toyproject.pos.common.exception.ProductNotFoundException;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import com.toyproject.pos.domain.dto.product.request.ProductReqDto;
import com.toyproject.pos.repository.interfaces.CartRepository;
import com.toyproject.pos.service.interfaces.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상품 담기의 예외 처리 단위 테스트 (Spring 컨텍스트 없음).
 * DB 장애는 통합 테스트로 재현하기 어려워 ProductService 를 목으로 대체한다.
 */
class CartServiceImplTest {

    private static final String BARCODE = "8800000000001";

    private final ProductService productService = mock(ProductService.class);
    private final CartRepository cartRepository = mock(CartRepository.class);
    private final CartServiceImpl cartService = new CartServiceImpl(productService, cartRepository);

    @Test
    @DisplayName("상품 조회 중 DB 오류는 삼키지 않고 그대로 전파한다")
    void addItem_propagatesDataAccessError() {
        when(productService.findProduct(BARCODE)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> cartService.addItem(request()))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(cartRepository, never()).addOrIncrease(any(), any());
    }

    @Test
    @DisplayName("미등록 상품은 ProductNotFoundException (전역 처리기가 404로 응답)")
    void addItem_unknownProduct_throwsNotFound() {
        when(productService.findProduct(BARCODE)).thenReturn(null);

        assertThatThrownBy(() -> cartService.addItem(request())).isInstanceOf(ProductNotFoundException.class);
        verify(cartRepository, never()).addOrIncrease(any(), any());
    }

    @Test
    @DisplayName("판매중지 상품은 ProductDiscontinuedException (전역 처리기가 409로 응답)")
    void addItem_discontinuedProduct_throwsDiscontinued() {
        when(productService.findProduct(BARCODE)).thenReturn(product("discontinued"));

        assertThatThrownBy(() -> cartService.addItem(request())).isInstanceOf(ProductDiscontinuedException.class);
        verify(cartRepository, never()).addOrIncrease(any(), any());
    }

    @Test
    @DisplayName("정상 상품은 true, 저장소에 담긴다")
    void addItem_normalProduct_returnsTrue() {
        when(productService.findProduct(BARCODE)).thenReturn(product("normal"));

        assertThat(cartService.addItem(request())).isTrue();
        verify(cartRepository).addOrIncrease(any(), any());
    }

    private static ProductReqDto request() {
        return ProductReqDto.builder()
                .cartInfoReqDto(new CartInfoReqDto("S01", "01", "9001"))
                .barcode(BARCODE)
                .build();
    }

    private static ProductQueryDto product(String status) {
        return new ProductQueryDto("P001", BARCODE, "테스트 감자칩", new BigDecimal("1500"), "1", "1", status);
    }
}
