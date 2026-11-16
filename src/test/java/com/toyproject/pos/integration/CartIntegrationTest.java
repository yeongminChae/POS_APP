package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 장바구니 API 의 현재 동작 고정. 버그로 보이는 동작은 [현재 동작/버그] 로 표시하고 그대로 기록한다.
 */
class CartIntegrationTest extends PosApiTestSupport {

    // ===== 특수 상품 =====

    @Test
    @DisplayName("[현재 동작/버그] 성인 전용 상품(…009)이 인증 없이 담긴다")
    void adultOnlyProduct_isAddedWithoutCheck() throws Exception {
        String tranNo = newTranNo();

        JsonNode res = json(addItem(POS_1, tranNo, ADULT_SOJU));

        assertThat(res.get("status").asInt()).isEqualTo(200);
        assertThat(json(getCart(POS_1, tranNo)).at("/linesList/0/barcode").asText()).isEqualTo(ADULT_SOJU);
    }

    @Test
    @DisplayName("판매중지 상품(…010)은 담을 수 없다 — 409 PRODUCT_DISCONTINUED, 카트·재고 변화 없음")
    void discontinuedProduct_isRejected() throws Exception {
        String tranNo = newTranNo();

        error(addItem(POS_1, tranNo, DISCONTINUED), 409, "PRODUCT_DISCONTINUED");

        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).isEmpty();
        assertThat(stock("P010")).isZero();
    }

    @Test
    @DisplayName("발주중단(suspend_order)·상태 없음(NULL) 상품은 판매할 수 있다")
    void suspendOrderAndNullStatus_areSellable() throws Exception {
        String original = jdbc.queryForObject("SELECT PRODUCT_STATUS FROM PRODUCT WHERE PRODUCT_ID = 'P004'", String.class);
        try {
            for (String status : new String[]{"suspend_order", null}) {
                jdbc.update("UPDATE PRODUCT SET PRODUCT_STATUS = ? WHERE PRODUCT_ID = 'P004'", status);
                String tranNo = newTranNo();

                assertThat(json(addItem(POS_1, tranNo, "8800000000004")).get("status").asInt()).isEqualTo(200);
            }
        } finally {
            jdbc.update("UPDATE PRODUCT SET PRODUCT_STATUS = ? WHERE PRODUCT_ID = 'P004'", original);
        }
    }

    @Test
    @DisplayName("결제하면 정상 라인 수량만큼 INVENTORY 재고가 차감된다")
    void checkout_decreasesStock() throws Exception {
        String tranNo = newTranNo();
        int before = stock("P003");

        addItem(POS_1, tranNo, COOKIE);
        updateQty(POS_1, tranNo, COOKIE, 4);
        checkoutCash(POS_1, tranNo, 20000);

        assertThat(stock("P003")).isEqualTo(before - 4);
    }

    @Test
    @DisplayName("취소한 라인은 재고를 차감하지 않는다")
    void checkout_skipsCancelledLines() throws Exception {
        String tranNo = newTranNo();
        int chocoBefore = stock("P002");
        int cookieBefore = stock("P003");

        addItem(POS_1, tranNo, CHOCO);
        cancelItem(POS_1, tranNo, CHOCO);
        addItem(POS_1, tranNo, COOKIE);
        checkoutCash(POS_1, tranNo, 5000);

        assertThat(stock("P002")).isEqualTo(chocoBefore);
        assertThat(stock("P003")).isEqualTo(cookieBefore - 1);
    }

    @Test
    @DisplayName("미등록 바코드: 404 PRODUCT_NOT_FOUND, 카트 변화 없음")
    void unknownBarcode_returns404() throws Exception {
        String tranNo = newTranNo();

        JsonNode err = error(addItem(POS_1, tranNo, UNKNOWN), 404, "PRODUCT_NOT_FOUND");

        assertThat(err.get("detail").asText()).isEqualTo("상품을 찾을 수 없습니다. barcode=" + UNKNOWN);
        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).isEmpty();
    }

    @Test
    @DisplayName("일괄 등록에 미등록 바코드가 섞이면 404 PRODUCT_NOT_FOUND, 아무것도 담지 않는다 (전부 아니면 전무)")
    void addMany_withUnknown_addsNothing() throws Exception {
        String tranNo = newTranNo();

        error(addItems(POS_1, tranNo, CHIPS, UNKNOWN, CHOCO), 404, "PRODUCT_NOT_FOUND");

        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).isEmpty();
    }

    // ===== 수량 변경 / 취소 =====

    @Test
    @DisplayName("수량 변경: 0·음수·카트에 없는 상품·취소된 상품은 false")
    void updateQty_rejectedCases() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        addItem(POS_1, tranNo, CHOCO);
        cancelItem(POS_1, tranNo, CHOCO);

        assertThat(json(updateQty(POS_1, tranNo, CHIPS, 0)).asBoolean()).isFalse();
        assertThat(json(updateQty(POS_1, tranNo, CHIPS, -1)).asBoolean()).isFalse();
        assertThat(json(updateQty(POS_1, tranNo, COOKIE, 2)).asBoolean()).isFalse();
        assertThat(json(updateQty(POS_1, tranNo, CHOCO, 2)).asBoolean()).isFalse();
        assertThat(json(getCart(POS_1, tranNo)).at("/linesList/0/qty").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("[현재 동작/버그] 수량 변경 요청에 qty 를 빼면 NPE → 500 INTERNAL_ERROR (B13, A6 요청 검증에서 400으로 바뀜)")
    void updateQty_withoutQtyField_returns500() throws Exception {
        String tranNo = newTranNo();

        error(perform(post("/api/cart/item/update/qty"), with(key(POS_1, tranNo), "barcode", CHIPS)), 500, "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("취소: 카트에 없는 상품·이미 취소된 상품은 false, 취소 후 재스캔하면 새 라인 생성")
    void cancel_andRescan() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHOCO);

        assertThat(json(cancelItem(POS_1, tranNo, COOKIE)).asBoolean()).isFalse();
        assertThat(json(cancelItem(POS_1, tranNo, CHOCO)).asBoolean()).isTrue();
        assertThat(json(cancelItem(POS_1, tranNo, CHOCO)).asBoolean()).isFalse();

        addItem(POS_1, tranNo, CHOCO);

        JsonNode lines = json(getCart(POS_1, tranNo)).get("linesList");
        assertThat(lines).extracting(l -> l.get("lineId").asText()).containsExactly("0001", "0002");
        assertThat(lines).extracting(l -> l.get("itemStatus").asText()).containsExactly("CANCELLED", "NORMAL");
    }

    @Test
    @DisplayName("[현재 동작] 장바구니 조회 응답에 내부 필드(barcodeToLineMap, seq)가 노출된다")
    void getCart_exposesInternals() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        JsonNode cart = json(getCart(POS_1, tranNo));

        assertThat(cart.get("barcodeToLineMap").get(CHIPS).asText()).isEqualTo("0001");
        assertThat(cart.get("seq").asInt()).isEqualTo(1);
    }

    // ===== 거래 키 =====

    @Test
    @DisplayName("같은 tranNo 라도 posNo 가 다르면 장바구니가 분리되고, 결제는 요청한 POS 번호로 저장된다")
    void sameTranNo_differentPos_isolated() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        addItem(POS_2, tranNo, CHOCO);

        JsonNode pos2Cart = json(getCart(POS_2, tranNo));
        assertThat(pos2Cart.get("posNo").asText()).isEqualTo(POS_2);
        assertThat(pos2Cart.get("linesList"))
                .extracting(l -> l.get("barcode").asText())
                .containsExactly(CHOCO);

        JsonNode sale = json(checkoutCash(POS_2, tranNo, 5000));
        assertThat(sale.get("posNo").asText()).isEqualTo(POS_2);
        assertThat(countSales(POS_2, tranNo)).isEqualTo(1);
        assertThat(countSales(POS_1, tranNo)).isZero();

        // POS 01 의 장바구니는 그대로 남음
        assertThat(json(getCart(POS_1, tranNo)).get("linesList"))
                .extracting(l -> l.get("barcode").asText())
                .containsExactly(CHIPS);
    }

    @Test
    @DisplayName("같은 tranNo·posNo 라도 점포(strCd)가 다르면 장바구니가 분리된다")
    void sameTranNo_differentStore_isolated() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        var otherStore = with(with(key(POS_1, tranNo), "strCd", "S99"), "barcode", CHOCO);
        perform(post("/api/cart/item/add"), otherStore);

        JsonNode cart = json(getCart(POS_1, tranNo));
        assertThat(cart.get("strCd").asText()).isEqualTo(STORE);
        assertThat(cart.get("linesList"))
                .extracting(l -> l.get("barcode").asText())
                .containsExactly(CHIPS);
    }
}
