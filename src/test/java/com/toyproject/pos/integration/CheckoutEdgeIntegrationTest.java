package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 결제/조회 API 의 경계 케이스 현재 동작 고정.
 * 실패는 전역 예외 처리기가 HTTP 상태 + ProblemDetail(code) 로 응답한다 (docs/api-change-plan.md 2.2절).
 */
class CheckoutEdgeIntegrationTest extends PosApiTestSupport {

    @Test
    @DisplayName("받은 금액이 결제액보다 적으면 400 INSUFFICIENT_CASH, 저장 없음, 장바구니 유지")
    void insufficientCash_isRejected() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS); // 1500

        JsonNode err = error(checkoutCash(POS_1, tranNo, 1000), 400, "INSUFFICIENT_CASH");
        assertThat(err.get("detail").asText()).isEqualTo("받은 금액이 결제 금액보다 적습니다.");

        assertThat(countSales(POS_1, tranNo)).isZero();
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).hasSize(1);
    }

    @Test
    @DisplayName("받은 금액이 결제액과 같으면 승인, 거스름돈 0")
    void exactCash_isApproved() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS); // 1500

        JsonNode sale = json(checkoutCash(POS_1, tranNo, 1500));

        assertThat(sale.at("/payment/status").asText()).isEqualTo("APPROVED");
        assertThat(amt(sale.at("/payment/cash/change"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("받은 금액 0 이하는 400 INVALID_CASH_AMOUNT")
    void zeroCash_isRejected() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS); // 빈 카트 검사(B5)보다 받은 금액 검사가 먼저 보이도록 상품을 담는다

        JsonNode err = error(checkoutCash(POS_1, tranNo, 0), 400, "INVALID_CASH_AMOUNT");
        assertThat(err.get("detail").asText()).isEqualTo("지불 금액이 이상 합니다. 확인 부탁 드립니다.");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("빈 장바구니 결제는 400 EMPTY_CART, 저장 없음")
    void emptyCart_isRejected() throws Exception {
        String tranNo = newTranNo();

        JsonNode err = error(checkoutCash(POS_1, tranNo, 1000), 400, "EMPTY_CART");
        assertThat(err.get("detail").asText()).isEqualTo("결제할 상품이 없습니다.");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("담은 상품을 모두 취소한 장바구니도 400 EMPTY_CART")
    void allCancelledCart_isRejected() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        cancelItem(POS_1, tranNo, CHIPS);

        error(checkoutCash(POS_1, tranNo, 1000), 400, "EMPTY_CART");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("카드 결제는 미구현 — 400 UNSUPPORTED_PAYMENT_METHOD, 저장 없음, 장바구니 유지")
    void cardPayment_isUnsupported() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        error(perform(post("/api/checkout"), with(key(POS_1, tranNo), "paymentMethod", "CARD")),
                400, "UNSUPPORTED_PAYMENT_METHOD");

        assertThat(countSales(POS_1, tranNo)).isZero();
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 거래 조회는 404 TRANSACTION_NOT_FOUND")
    void callUnknownTransaction_returns404() throws Exception {
        error(callTransaction(POS_1, "NO-SUCH-TRAN"), 404, "TRANSACTION_NOT_FOUND");
    }

    @Test
    @DisplayName("존재하지 않는 원거래 반품은 404 TRANSACTION_NOT_FOUND, 저장 없음")
    void refundUnknownTransaction_returns404() throws Exception {
        String refundTranNo = newTranNo();

        error(refund(POS_1, refundTranNo, POS_1, "NO-SUCH-TRAN"), 404, "TRANSACTION_NOT_FOUND");
        assertThat(countSales(POS_1, refundTranNo)).isZero();
    }

    @Test
    @DisplayName("결제 완료된 tranNo 로 다시 결제하면 409 DUPLICATE_TRANSACTION, 저장 없음, 장바구니 유지")
    void reuseTranNoAfterCheckout_isRejected() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        checkoutCash(POS_1, tranNo, 2000);

        // 결제 후 같은 키로 담기는 막히지 않는다
        ok(addItem(POS_1, tranNo, CHIPS));

        JsonNode err = error(checkoutCash(POS_1, tranNo, 2000), 409, "DUPLICATE_TRANSACTION");
        assertThat(err.get("detail").asText()).isEqualTo("이미 결제된 거래번호입니다.");

        assertThat(countSales(POS_1, tranNo)).isEqualTo(1);
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).hasSize(1); // 롤백되어 카트 유지
    }

    @Test
    @DisplayName("결제가 실패해 롤백되면 재고도 차감되지 않는다")
    void failedCheckout_doesNotDecreaseStock() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        checkoutCash(POS_1, tranNo, 2000);
        int afterFirst = stock("P001");

        addItem(POS_1, tranNo, CHIPS); // 같은 tranNo 재사용 → 409 DUPLICATE_TRANSACTION
        error(checkoutCash(POS_1, tranNo, 2000), 409, "DUPLICATE_TRANSACTION");

        assertThat(stock("P001")).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("[현재 동작] 결제 요약 API 는 경로의 posNo/tranNo 를 무시하고 바디의 키를 쓴다")
    void summary_ignoresPathVariables() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        JsonNode summary = json(perform(get("/api/checkout/{posNo}/{tranNo}/summary", "99", "IGNORED"), key(POS_1, tranNo)));

        assertThat(summary.get("tranNo").asText()).isEqualTo(tranNo);
        assertThat(amt(summary.get("totalAmount"))).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("결제 요약 응답은 CartSummaryResDto — 거래 키를 담고, 라인 productId 는 내보내지 않는다")
    void summary_returnsSummaryDto() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        JsonNode summary = json(perform(get("/api/checkout/{posNo}/{tranNo}/summary", POS_1, tranNo), key(POS_1, tranNo)));

        assertThat(summary.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("strCd", "posNo", "tranNo", "totalQty", "totalAmount", "lines");
        assertThat(summary.at("/lines/0").has("productId")).isFalse();
        assertThat(summary.at("/lines/0/barcode").asText()).isEqualTo(CHIPS);
    }

    @Test
    @DisplayName("CASH_PAYMENTS.PAYMENT_ID 는 같은 결제의 PAYMENTS.PAYMENT_ID — 현금 상세가 없는 결제가 끼어도 어긋나지 않는다")
    void cashPaymentId_matchesPaymentId() throws Exception {
        String first = newTranNo();
        addItem(POS_1, first, CHIPS);
        checkoutCash(POS_1, first, 2000);

        // 현금 상세가 없는 결제 1건이 생긴 상황 (예: 향후 카드 결제)
        jdbc.update("INSERT INTO PAYMENTS (SALE_ID, METHOD, STATUS, AMOUNT_DUE_TO_PAY, APPROVED_AT) "
                + "VALUES (?, 'CARD', 'APPROVED', 0, '2025-01-01 00:00:00')", saleId(POS_1, first));

        String second = newTranNo();
        addItem(POS_1, second, CHIPS);
        checkoutCash(POS_1, second, 2000);

        long paymentId = jdbc.queryForObject(
                "SELECT PAYMENT_ID FROM PAYMENTS WHERE SALE_ID = ?", Long.class, saleId(POS_1, second));
        assertThat(amt(jdbc.queryForObject(
                "SELECT AMOUNT_RECEIVED FROM CASH_PAYMENTS WHERE PAYMENT_ID = ?", Object.class, paymentId)))
                .isEqualByComparingTo("2000");

        JsonNode called = json(callTransaction(POS_1, second));
        assertThat(amt(called.at("/payment/cash/change"))).isEqualByComparingTo("500");
    }
}
