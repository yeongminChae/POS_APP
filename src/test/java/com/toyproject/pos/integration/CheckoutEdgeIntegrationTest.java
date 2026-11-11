package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 결제/조회 API 의 경계 케이스 현재 동작 고정.
 * 예외 케이스는 전역 예외 처리가 없어 MockMvc 에서 ServletException 으로 전파된다 (실제 서버에서는 500).
 */
class CheckoutEdgeIntegrationTest extends PosApiTestSupport {

    @Test
    @DisplayName("받은 금액이 결제액보다 적으면 IllegalArgumentException(500), 저장 없음, 장바구니 유지")
    void insufficientCash_isRejected() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS); // 1500

        assertThatThrownBy(() -> checkoutCash(POS_1, tranNo, 1000))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("받은 금액이 결제 금액보다 적습니다.");

        assertThat(countSales(POS_1, tranNo)).isZero();
        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).hasSize(1);
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
    @DisplayName("받은 금액 0 이하는 IllegalArgumentException(500)")
    void zeroCash_throws() {
        String tranNo = newTranNo();

        assertThatThrownBy(() -> checkoutCash(POS_1, tranNo, 0))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("지불 금액이 이상 합니다. 확인 부탁 드립니다.");
        assertThat(countSales(POS_1, tranNo)).isZero();
    }

    @Test
    @DisplayName("[현재 동작/버그] 빈 장바구니도 결제되어 0원 거래가 저장된다")
    void emptyCart_isCheckedOut() throws Exception {
        String tranNo = newTranNo();

        JsonNode sale = json(checkoutCash(POS_1, tranNo, 1000));

        assertThat(sale.at("/cart/lines")).isEmpty();
        assertThat(amt(saleRow(POS_1, tranNo).get("TOTAL_AMOUNT"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("[현재 동작/버그] 카드 결제는 미구현 — Payment 가 null 이라 NPE(500), 저장 없음, 장바구니 유지")
    void cardPayment_throwsNpe() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        assertThatThrownBy(() -> perform(post("/api/checkout"), with(key(POS_1, tranNo), "paymentMethod", "CARD")))
                .hasRootCauseInstanceOf(NullPointerException.class);

        assertThat(countSales(POS_1, tranNo)).isZero();
        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).hasSize(1);
    }

    @Test
    @DisplayName("[현재 동작/버그] 존재하지 않는 거래 조회는 NPE(500)")
    void callUnknownTransaction_throwsNpe() {
        assertThatThrownBy(() -> callTransaction(POS_1, "NO-SUCH-TRAN"))
                .hasRootCauseInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("[현재 동작/버그] 결제 완료된 tranNo 로 다시 담고 결제하면 UNIQUE 제약 위반(500), 장바구니는 남는다")
    void reuseTranNoAfterCheckout_violatesUnique() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        checkoutCash(POS_1, tranNo, 2000);

        // 결제 후 같은 키로 담기는 막히지 않는다
        assertThat(json(addItem(POS_1, tranNo, CHIPS)).get("status").asInt()).isEqualTo(200);

        assertThatThrownBy(() -> checkoutCash(POS_1, tranNo, 2000))
                .hasRootCauseInstanceOf(SQLiteException.class)
                .rootCause().hasMessageContaining("UNIQUE constraint failed: SALES.STORE_CD, SALES.POS_NO, SALES.TRAN_NO");

        assertThat(countSales(POS_1, tranNo)).isEqualTo(1);
        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).hasSize(1); // 롤백되어 카트 유지
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
