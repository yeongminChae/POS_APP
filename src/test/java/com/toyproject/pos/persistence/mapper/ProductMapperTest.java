package com.toyproject.pos.persistence.mapper;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductMapperTest {

    @Autowired
    ProductMapper productMapper;

    @Test
    void findByBarcode_returnsSeededProduct() {
        ProductQueryDto product = productMapper.findByBarcode("8800000000001");

        assertThat(product).isNotNull();
        assertThat(product.getProductId()).isEqualTo("P001");
        assertThat(product.getName()).isEqualTo("테스트 감자칩");
        assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(product.getBrandId()).isEqualTo("1");
        assertThat(product.getCategoryId()).isEqualTo("1");
    }

    @Test
    void findByBarcode_returnsNullForUnknownBarcode() {
        assertThat(productMapper.findByBarcode("8800000009999")).isNull();
    }

}
