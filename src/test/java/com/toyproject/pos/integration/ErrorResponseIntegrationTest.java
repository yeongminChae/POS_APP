package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 전역 예외 처리기의 공통 오류 응답 (ProblemDetail + code). 업무 오류별 응답은 각 시나리오 테스트에서 검사한다.
 */
class ErrorResponseIntegrationTest extends PosApiTestSupport {

    @Test
    @DisplayName("JSON 문법이 깨진 요청은 400 MALFORMED_REQUEST")
    void malformedJson_returns400() throws Exception {
        JsonNode err = error(mvc.perform(post(SALES)
                .contentType(MediaType.APPLICATION_JSON).content("{bad json")), 400, "MALFORMED_REQUEST");

        assertThat(err.get("detail").asText()).isEqualTo("요청 형식이 올바르지 않습니다.");
        assertThat(err.get("instance").asText()).isEqualTo(SALES);
    }

    @Test
    @DisplayName("없는 결제 수단 값은 400 MALFORMED_REQUEST")
    void unknownPaymentMethod_returns400() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        error(perform(post(SALES), with(with(key(POS_1, tranNo), "paymentMethod", "BITCOIN"), "amountReceived", 2000)),
                400, "MALFORMED_REQUEST");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("옛 경로(/api/cart/item/add, /api/checkout)는 남기지 않는다 — 404")
    void oldPaths_return404() throws Exception {
        String tranNo = newTranNo();

        assertThat(perform(post("/api/cart/item/add"), with(key(POS_1, tranNo), "barcode", CHIPS))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(perform(post("/api/checkout"), with(with(key(POS_1, tranNo), "paymentMethod", "CASH"), "amountReceived", 2000))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).isEmpty();
    }
}
