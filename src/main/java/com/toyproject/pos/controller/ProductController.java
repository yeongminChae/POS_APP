package com.toyproject.pos.controller;

import com.toyproject.pos.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 API — {@code /api/products}. 경로만 잡아 두었고 엔드포인트는 아직 없다.
 * 키오스크 단계에서 메뉴(상품) 목록 조회 등을 추가할 자리다.
 */
@RestController
@RequestMapping("/api/products")
@Validated
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

}
