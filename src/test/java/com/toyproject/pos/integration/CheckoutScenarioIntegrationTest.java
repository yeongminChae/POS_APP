package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 거래 시작 → 상품 담기 → 수량 변경 → 상품 취소 → 현금 결제 → 거래 조회 → 반품
 * 전체 흐름의 "현재 동작"을 고정하는 테스트. 버그로 보이는 동작도 고치지 않고 그대로 기록한다 ([현재 동작] 표시).
 */
class CheckoutScenarioIntegrationTest extends PosApiTestSupport {

    @Test
    @DisplayName("전체 시나리오: 담기/수량변경/취소 → 현금결제 → DB 저장 → 조회 → 반품")
    void fullScenario() throws Exception {
        String tranNo = newTranNo();

        // 1) 거래 시작 — 별도 API 없음. 장바구니 조회 시 빈 카트가 생성된다.
        JsonNode empty = json(getCart(POS_1, tranNo));
        assertThat(empty.get("lines")).isEmpty();
        assertThat(amt(empty.get("totalAmount"))).isEqualByComparingTo("0");

        // 2) 상품 담기 — 같은 바코드 재스캔은 수량 증가
        assertThat(ok(addItem(POS_1, tranNo, CHIPS)).get("totalQty").asInt()).isEqualTo(1);
        addItem(POS_1, tranNo, CHIPS);
        addItem(POS_1, tranNo, CHOCO);
        addItem(POS_1, tranNo, COOKIE);

        // 3) 수량 변경 (절대값 설정)
        assertThat(ok(updateQty(POS_1, tranNo, COOKIE, 3)).at("/lines/2/qty").asInt()).isEqualTo(3);

        // 4) 상품 취소 (지정 취소)
        assertThat(ok(cancelItem(POS_1, tranNo, CHOCO)).at("/lines/1/itemStatus").asText()).isEqualTo("CANCELLED");

        JsonNode cart = json(getCart(POS_1, tranNo));
        JsonNode lines = cart.get("lines");
        assertThat(lines).hasSize(3);
        assertLine(lines.get(0), "0001", CHIPS, 2, "3000", "NORMAL");
        assertLine(lines.get(1), "0002", CHOCO, 1, "0", "CANCELLED"); // [현재 동작] 취소 라인은 수량 유지, 금액만 0
        assertLine(lines.get(2), "0003", COOKIE, 3, "7500", "NORMAL");
        assertThat(cart.get("totalQty").asInt()).isEqualTo(5);
        assertThat(amt(cart.get("totalAmount"))).isEqualByComparingTo("10500");

        // 5) 현금 결제
        JsonNode sale = json(checkoutCash(POS_1, tranNo, 20000));
        assertThat(sale.get("saleStatus").asText()).isEqualTo("NORMAL");
        assertThat(sale.get("receiptNo").asText()).matches("S01-\\d{8}-01-" + tranNo);
        assertThat(amt(sale.at("/payment/amountDueToPay"))).isEqualByComparingTo("10500");
        assertThat(amt(sale.at("/payment/cash/amountReceived"))).isEqualByComparingTo("20000");
        assertThat(amt(sale.at("/payment/cash/change"))).isEqualByComparingTo("9500");

        // 5-1) DB 저장 결과 직접 검증
        Map<String, Object> saleRow = saleRow(POS_1, tranNo);
        long saleId = ((Number) saleRow.get("SALE_ID")).longValue();
        assertThat(saleRow.get("SALE_STATUS")).isEqualTo("NORMAL");
        assertThat(saleRow.get("TOTAL_QTY")).isEqualTo(5);
        assertThat(amt(saleRow.get("TOTAL_AMOUNT"))).isEqualByComparingTo("10500");
        assertThat(saleRow.get("ORIG_SALE_ID")).isNull();
        assertThat(saleRow.get("CASHIER")).isEqualTo("테스터"); // [현재 동작] 계산원 하드코딩
        assertThat(saleRow.get("RECEIPT_NO")).isEqualTo(sale.get("receiptNo").asText());

        List<Map<String, Object>> lineRows = jdbc.queryForList(
                "SELECT * FROM SALE_LINES WHERE SALE_ID = ? ORDER BY LINE_NO", saleId);
        assertThat(lineRows).extracting(r -> r.get("LINE_NO")).containsExactly("0001", "0002", "0003");
        assertThat(lineRows).extracting(r -> r.get("ITEM_STATUS")).containsExactly("NORMAL", "CANCELLED", "NORMAL");
        assertThat(lineRows).extracting(r -> r.get("QTY")).containsExactly(2, 1, 3); // 취소 라인도 저장됨
        assertThat(lineRows).extracting(r -> amt(r.get("AMOUNT")).intValue()).containsExactly(3000, 0, 7500);

        Map<String, Object> payment = jdbc.queryForMap("SELECT * FROM PAYMENTS WHERE SALE_ID = ?", saleId);
        assertThat(payment.get("METHOD")).isEqualTo("CASH");
        assertThat(payment.get("STATUS")).isEqualTo("APPROVED");
        assertThat(amt(payment.get("AMOUNT_DUE_TO_PAY"))).isEqualByComparingTo("10500");

        // CASH_PAYMENTS.PAYMENT_ID 는 같은 결제의 PAYMENTS.PAYMENT_ID
        Map<String, Object> cash = jdbc.queryForMap(
                "SELECT * FROM CASH_PAYMENTS WHERE PAYMENT_ID = ?", payment.get("PAYMENT_ID"));
        assertThat(amt(cash.get("AMOUNT_RECEIVED"))).isEqualByComparingTo("20000");
        assertThat(amt(cash.get("CHANGE"))).isEqualByComparingTo("9500");

        Map<String, Object> journal = jdbc.queryForMap("SELECT * FROM RECEIPT_JOURNAL WHERE SALE_ID = ?", saleId);
        assertThat(journal.get("RECEIPT_NO")).isEqualTo(saleRow.get("RECEIPT_NO"));
        assertThat(journal.get("TRAN_NO")).isEqualTo(tranNo);
        assertThat(journal.get("BODY_TEXT")).isNull();
        JsonNode trJson = objectMapper.readTree((String) journal.get("TR_JSON"));
        assertThat(trJson.get("saleId").asLong()).isEqualTo(saleId);
        assertThat(trJson.at("/cartSummary/lines")).hasSize(3);

        // 5-2) 커밋 후 인메모리 장바구니 비워짐 (같은 키로 조회하면 새 빈 카트)
        assertThat(json(getCart(POS_1, tranNo)).get("lines")).isEmpty();

        // 6) 거래 조회 — DB 에서 재구성
        JsonNode called = json(callTransaction(POS_1, tranNo));
        assertThat(called.get("receiptNo").asText()).isEqualTo(sale.get("receiptNo").asText());
        assertThat(called.at("/cart/lines")).hasSize(3);
        assertThat(called.at("/cart/lines/1/itemStatus").asText()).isEqualTo("CANCELLED");
        assertThat(amt(called.at("/cart/totalAmount"))).isEqualByComparingTo("10500");
        assertThat(amt(called.at("/payment/cash/change"))).isEqualByComparingTo("9500");

        // 7) 반품 — 새 거래번호로 반품 거래 생성
        String refundTranNo = newTranNo();
        JsonNode refund = json(refund(POS_1, refundTranNo, POS_1, tranNo));
        assertThat(refund.get("saleStatus").asText()).isEqualTo("REFUNDED");
        assertThat(refund.get("origSaleId").asLong()).isEqualTo(saleId);
        assertThat(refund.get("tranNo").asText()).isEqualTo(refundTranNo);
        assertThat(refund.at("/cart/totalQty").asInt()).isEqualTo(-5);
        assertThat(amt(refund.at("/cart/totalAmount"))).isEqualByComparingTo("-10500");
        // 반품은 결제액만큼 환불: 청구액·받은 금액 모두 음수, 거스름돈 0
        assertThat(amt(refund.at("/payment/amountDueToPay"))).isEqualByComparingTo("-10500");
        assertThat(amt(refund.at("/payment/cash/amountReceived"))).isEqualByComparingTo("-10500");
        assertThat(amt(refund.at("/payment/cash/change"))).isEqualByComparingTo("0");

        Map<String, Object> refundRow = saleRow(POS_1, refundTranNo);
        long refundSaleId = ((Number) refundRow.get("SALE_ID")).longValue();
        assertThat(refundRow.get("SALE_STATUS")).isEqualTo("REFUNDED");
        assertThat(((Number) refundRow.get("ORIG_SALE_ID")).longValue()).isEqualTo(saleId);
        assertThat(refundRow.get("TOTAL_QTY")).isEqualTo(-5);
        assertThat(amt(refundRow.get("TOTAL_AMOUNT"))).isEqualByComparingTo("-10500");

        List<Map<String, Object>> refundLines = jdbc.queryForList(
                "SELECT * FROM SALE_LINES WHERE SALE_ID = ? ORDER BY LINE_NO", refundSaleId);
        assertThat(refundLines).extracting(r -> r.get("ITEM_STATUS")).containsExactly("REFUNDED", "CANCELLED", "REFUNDED");
        assertThat(refundLines).extracting(r -> r.get("QTY")).containsExactly(-2, 1, -3);

        Map<String, Object> refundPayment = jdbc.queryForMap("SELECT * FROM PAYMENTS WHERE SALE_ID = ?", refundSaleId);
        assertThat(refundPayment.get("STATUS")).isEqualTo("REFUNDED");
        assertThat(amt(refundPayment.get("AMOUNT_DUE_TO_PAY"))).isEqualByComparingTo("-10500");

        Map<String, Object> refundCash = jdbc.queryForMap(
                "SELECT * FROM CASH_PAYMENTS WHERE PAYMENT_ID = ?", refundPayment.get("PAYMENT_ID"));
        // 받은 돈(20000)이 아니라 결제액(10500)을 환불
        assertThat(amt(refundCash.get("AMOUNT_RECEIVED"))).isEqualByComparingTo("-10500");
        assertThat(amt(refundCash.get("CHANGE"))).isEqualByComparingTo("0");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM RECEIPT_JOURNAL WHERE SALE_ID = ?", Integer.class, refundSaleId))
                .isEqualTo(1);
        JsonNode refundTrJson = objectMapper.readTree(jdbc.queryForObject(
                "SELECT TR_JSON FROM RECEIPT_JOURNAL WHERE SALE_ID = ?", String.class, refundSaleId));
        assertThat(refundTrJson.get("saleId").asLong()).isEqualTo(refundSaleId);

        // 반품되면 원거래 상태도 REFUNDED
        assertThat(saleRow(POS_1, tranNo).get("SALE_STATUS")).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("같은 원거래를 두 번 반품하면 409 ALREADY_REFUNDED, 두 번째 반품은 저장되지 않는다")
    void refundTwice_isRejected() throws Exception {
        String tranNo = paidSale(CHIPS);

        refund(POS_1, newTranNo(), POS_1, tranNo);
        String secondRefund = newTranNo();

        JsonNode err = error(refund(POS_1, secondRefund, POS_1, tranNo), 409, "ALREADY_REFUNDED");
        assertThat(err.get("detail").asText()).isEqualTo("이미 반품된 거래입니다.");
        assertThat(countSales(POS_1, secondRefund)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SALES WHERE ORIG_SALE_ID = ?", Integer.class,
                saleId(POS_1, tranNo))).isEqualTo(1);
    }

    @Test
    @DisplayName("반품 거래(REFUNDED)를 다시 반품하면 409 ALREADY_REFUNDED")
    void refundOfRefund_isRejected() throws Exception {
        String tranNo = paidSale(CHIPS);
        String refundTranNo = newTranNo();
        refund(POS_1, refundTranNo, POS_1, tranNo);

        error(refund(POS_1, newTranNo(), POS_1, refundTranNo), 409, "ALREADY_REFUNDED");
    }

    @Test
    @DisplayName("반품하면 반품 수량만큼 재고가 복원된다 (취소 라인은 제외)")
    void refund_restoresStock() throws Exception {
        String tranNo = newTranNo();
        int chipsBefore = stock("P001");
        int chocoBefore = stock("P002");
        addItem(POS_1, tranNo, CHIPS);
        addItem(POS_1, tranNo, CHIPS);
        addItem(POS_1, tranNo, CHOCO);
        cancelItem(POS_1, tranNo, CHOCO);
        checkoutCash(POS_1, tranNo, 5000);
        assertThat(stock("P001")).isEqualTo(chipsBefore - 2);

        refund(POS_1, newTranNo(), POS_1, tranNo);

        assertThat(stock("P001")).isEqualTo(chipsBefore);
        assertThat(stock("P002")).isEqualTo(chocoBefore);
    }

    @Test
    @DisplayName("반품 거래의 승인 시각과 결제 거래 키는 원거래가 아니라 반품 기준이다")
    void refund_hasOwnApprovedAtAndKey() throws Exception {
        String tranNo = paidSale(CHIPS);
        long origSaleId = saleId(POS_1, tranNo);
        // 1초 경계에 기대지 않도록 원거래 승인 시각을 과거로 바꿔 둔다
        String past = "2000-01-01 00:00:00";
        jdbc.update("UPDATE PAYMENTS SET APPROVED_AT = ? WHERE SALE_ID = ?", past, origSaleId);
        jdbc.update("UPDATE SALES SET APPROVED_AT = ? WHERE SALE_ID = ?", past, origSaleId);

        String refundTranNo = newTranNo();
        JsonNode refund = json(refund(POS_1, refundTranNo, POS_1, tranNo));
        long refundSaleId = saleId(POS_1, refundTranNo);

        assertThat(refund.get("approvedAt").asText()).isNotEqualTo(past);
        assertThat(saleRow(POS_1, refundTranNo).get("APPROVED_AT")).isNotEqualTo(past);
        assertThat(jdbc.queryForObject("SELECT APPROVED_AT FROM PAYMENTS WHERE SALE_ID = ?", String.class, refundSaleId))
                .isNotEqualTo(past);
        assertThat(jdbc.queryForObject("SELECT APPROVED_AT FROM RECEIPT_JOURNAL WHERE SALE_ID = ?", String.class, refundSaleId))
                .isNotEqualTo(past);

        JsonNode trJson = objectMapper.readTree(jdbc.queryForObject(
                "SELECT TR_JSON FROM RECEIPT_JOURNAL WHERE SALE_ID = ?", String.class, refundSaleId));
        assertThat(trJson.at("/payment/commonPayment/tranNo").asText()).isEqualTo(refundTranNo);
    }

    @Test
    @DisplayName("반품 거래도 조회된다 — 저장된 결제 상태·금액과 합계로 복원")
    void callRefundTransaction_returnsRefund() throws Exception {
        String tranNo = paidSale(CHIPS); // 1500원, 10000원 받음
        String refundTranNo = newTranNo();
        refund(POS_1, refundTranNo, POS_1, tranNo);

        JsonNode called = json(callTransaction(POS_1, refundTranNo));

        assertThat(called.get("saleStatus").asText()).isEqualTo("REFUNDED");
        assertThat(called.get("tranNo").asText()).isEqualTo(refundTranNo);
        assertThat(called.get("origSaleId").asLong()).isEqualTo(saleId(POS_1, tranNo));
        assertThat(called.at("/payment/status").asText()).isEqualTo("REFUNDED");
        assertThat(amt(called.at("/payment/amountDueToPay"))).isEqualByComparingTo("-1500");
        assertThat(amt(called.at("/payment/cash/amountReceived"))).isEqualByComparingTo("-1500");
        assertThat(amt(called.at("/payment/cash/change"))).isEqualByComparingTo("0");
        assertThat(called.at("/cart/totalQty").asInt()).isEqualTo(-1);
        assertThat(amt(called.at("/cart/totalAmount"))).isEqualByComparingTo("-1500");
        assertThat(called.at("/cart/lines/0/itemStatus").asText()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("반품 요청의 reason 이 그대로 저장된다 (TR_JSON 기준)")
    void refundReason_isSaved() throws Exception {
        String tranNo = paidSale(CHIPS);
        String refundTranNo = newTranNo();
        refund(POS_1, refundTranNo, POS_1, tranNo); // reason = "테스트 반품"

        assertThat(refundTrJson(refundTranNo).at("/refundDetail/reason").asText()).isEqualTo("테스트 반품");
    }

    @Test
    @DisplayName("반품 요청에 reason 이 없으면 null 로 저장된다")
    void refundWithoutReason_isNull() throws Exception {
        String tranNo = paidSale(CHIPS);
        String refundTranNo = newTranNo();
        Map<String, Object> body = key(POS_1, refundTranNo);
        body.put("origStrCd", STORE);
        body.put("origPosNo", POS_1);
        body.put("origTranNo", tranNo);
        perform(post(SALE_REFUNDS), body);

        assertThat(refundTrJson(refundTranNo).at("/refundDetail/reason").isNull()).isTrue();
    }

    private JsonNode refundTrJson(String refundTranNo) throws Exception {
        return objectMapper.readTree(jdbc.queryForObject(
                "SELECT TR_JSON FROM RECEIPT_JOURNAL WHERE SALE_ID = ?", String.class, saleId(POS_1, refundTranNo)));
    }

    @Test
    @DisplayName("반품 저장 중 한 단계라도 실패하면 반품 전체가 롤백되고 500 INTERNAL_ERROR (트리거로 CASH_PAYMENTS 단계 실패 주입)")
    void refund_isAtomic() throws Exception {
        String tranNo = paidSale(CHIPS);
        String refundTranNo = newTranNo();

        // 반품만 받은 금액이 음수이므로, 반품의 현금 상세 INSERT 만 실패한다
        jdbc.execute("CREATE TRIGGER fail_refund_cash BEFORE INSERT ON CASH_PAYMENTS "
                + "WHEN NEW.AMOUNT_RECEIVED < 0 BEGIN SELECT RAISE(ABORT, 'injected failure'); END");
        try {
            // 예상하지 못한 오류는 500 INTERNAL_ERROR, 내부 메시지는 응답에 노출하지 않음
            JsonNode err = error(refund(POS_1, refundTranNo, POS_1, tranNo), 500, "INTERNAL_ERROR");
            assertThat(err.get("detail").asText()).isEqualTo("서버 오류가 발생했습니다.");
            assertThat(err.toString()).doesNotContain("injected failure").doesNotContain("SQLITE");
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_refund_cash");
        }

        assertThat(countSales(POS_1, refundTranNo)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SALES WHERE ORIG_SALE_ID = ?", Integer.class,
                saleId(POS_1, tranNo))).isZero();
        assertThat(saleRow(POS_1, tranNo).get("SALE_STATUS")).isEqualTo("NORMAL"); // 원거래 상태 변경도 롤백
    }

    /** 단일 상품을 현금 결제까지 끝낸 거래번호를 반환 */
    private String paidSale(String barcode) throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, barcode);
        checkoutCash(POS_1, tranNo, 10000);
        return tranNo;
    }

    private static void assertLine(JsonNode line, String lineId, String barcode, int qty, String amount, String status) {
        assertThat(line.get("lineId").asText()).isEqualTo(lineId);
        assertThat(line.get("barcode").asText()).isEqualTo(barcode);
        assertThat(line.get("qty").asInt()).isEqualTo(qty);
        assertThat(amt(line.get("amount"))).isEqualByComparingTo(amount);
        assertThat(line.get("itemStatus").asText()).isEqualTo(status);
    }
}
