package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 요청 값 검증 (A6): 잘못된 요청은 400 VALIDATION_FAILED + errors[{field, message}].
 * field 는 JSON 필드 이름 (@JsonUnwrapped 로 펼친 거래 키도 `tranNo` 처럼, 목록 바디는 `[1].barcode` 처럼).
 */
class RequestValidationIntegrationTest extends PosApiTestSupport {

    // ===== 장바구니 =====

    @Test
    @DisplayName("상품 담기에 거래번호가 없으면 400, field = tranNo")
    void addItem_withoutTranNo_returns400() throws Exception {
        Map<String, Object> body = with(key(POS_1, null), "barcode", CHIPS);
        body.remove("tranNo");

        JsonNode err = error(perform(post(CART_LINES), body), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("tranNo: 거래번호는 필수입니다");
    }

    @Test
    @DisplayName("상품 담기에 바코드가 공백이면 400, field = barcode")
    void addItem_blankBarcode_returns400() throws Exception {
        String tranNo = newTranNo();

        JsonNode err = error(addItem(POS_1, tranNo, " "), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("barcode: 바코드는 필수입니다");
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).isEmpty();
    }

    @Test
    @DisplayName("일괄 담기에서 한 건만 바코드가 없어도 400, field = [1].barcode, 아무것도 담지 않는다")
    void addMany_withOneMissingBarcode_returns400AndAddsNothing() throws Exception {
        String tranNo = newTranNo();
        Map<String, Object> missing = key(POS_1, tranNo);

        JsonNode err = error(perform(post(CART_LINES_BATCH),
                List.of(with(key(POS_1, tranNo), "barcode", CHIPS), missing)), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("[1].barcode: 바코드는 필수입니다");
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).isEmpty();
    }

    @Test
    @DisplayName("일괄 담기에서 거래 키가 빠진 건은 field = [0].posNo")
    void addMany_withMissingKey_returns400() throws Exception {
        Map<String, Object> noPos = with(key(POS_1, newTranNo()), "barcode", CHIPS);
        noPos.remove("posNo");

        JsonNode err = error(perform(post(CART_LINES_BATCH), List.of(noPos)), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("[0].posNo: POS 번호는 필수입니다");
    }

    @Test
    @DisplayName("일괄 담기 목록이 비어 있으면 400")
    void addMany_emptyList_returns400() throws Exception {
        JsonNode err = error(perform(post(CART_LINES_BATCH), List.of()), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly(": 담을 상품이 없습니다");
    }

    // ===== 조회 (쿼리 파라미터) =====

    @Test
    @DisplayName("조회 API 에 거래 키 쿼리 파라미터가 없으면 400, field = 빠진 파라미터 이름")
    void query_withoutTranNo_returns400() throws Exception {
        for (String path : List.of(CARTS, CART_SUMMARY, SALES)) {
            JsonNode err = error(mvc.perform(get(path).param("strCd", STORE).param("posNo", POS_1)),
                    400, "VALIDATION_FAILED");

            assertThat(errors(err)).as(path).containsExactly("tranNo: 거래번호는 필수입니다");
        }
    }

    // ===== 결제·반품 =====

    @Test
    @DisplayName("현금 결제에 받은 금액이 없으면 400, field = amountReceived (Q9), 판매 저장 안 됨")
    void cashCheckout_withoutAmountReceived_returns400() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        JsonNode err = error(perform(post(SALES), with(key(POS_1, tranNo), "paymentMethod", "CASH")),
                400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("amountReceived: 현금 결제는 받은 금액이 필수입니다");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("결제 수단이 없으면 400, field = paymentMethod")
    void checkout_withoutPaymentMethod_returns400() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        JsonNode err = error(perform(post(SALES), with(key(POS_1, tranNo), "amountReceived", 2000)),
                400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("paymentMethod: 결제 수단은 필수입니다");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("반품 요청에 원거래 키가 없으면 400, 원거래는 반품되지 않는다")
    void refund_withoutOriginKey_returns400() throws Exception {
        String origTranNo = newTranNo();
        addItem(POS_1, origTranNo, CHIPS);
        checkoutCash(POS_1, origTranNo, 2000);

        Map<String, Object> body = key(POS_1, newTranNo());
        body.put("origStrCd", STORE);
        body.put("origPosNo", POS_1);

        JsonNode err = error(perform(post(SALE_REFUNDS), body), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("origTranNo: 원거래 거래번호는 필수입니다");
        assertThat(saleRow(POS_1, origTranNo).get("SALE_STATUS")).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("반품 사유가 100자를 넘으면 400, field = reason")
    void refund_tooLongReason_returns400() throws Exception {
        Map<String, Object> body = key(POS_1, newTranNo());
        body.put("origStrCd", STORE);
        body.put("origPosNo", POS_1);
        body.put("origTranNo", newTranNo());
        body.put("reason", "가".repeat(101));

        JsonNode err = error(perform(post(SALE_REFUNDS), body), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactly("reason: 반품 사유는 100자 이하여야 합니다");
    }

    @Test
    @DisplayName("여러 필드가 틀리면 errors 에 모두 담긴다")
    void multipleViolations_areAllReported() throws Exception {
        JsonNode err = error(perform(post(CART_LINES), Map.of("strCd", STORE)), 400, "VALIDATION_FAILED");

        assertThat(errors(err)).containsExactlyInAnyOrder(
                "posNo: POS 번호는 필수입니다",
                "tranNo: 거래번호는 필수입니다",
                "barcode: 바코드는 필수입니다");
    }

    /** errors[] 를 "field: message" 목록으로 */
    private static List<String> errors(JsonNode err) {
        assertThat(err.get("detail").asText()).isEqualTo("요청 값이 올바르지 않습니다.");
        return StreamSupport.stream(err.get("errors").spliterator(), false)
                .map(e -> e.get("field").asText() + ": " + e.get("message").asText())
                .toList();
    }
}
