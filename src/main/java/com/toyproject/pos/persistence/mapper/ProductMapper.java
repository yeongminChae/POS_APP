package com.toyproject.pos.persistence.mapper;

import com.toyproject.pos.domain.dto.product.ProductQueryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {
    ProductQueryDto findByBarcode(@Param("barcode") String barcode);

    int updateProductStock(@Param("productId") String productId,
                           @Param("qty") int updateQty);

}
