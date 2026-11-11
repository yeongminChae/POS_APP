package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @DisplayName("[현재 동작/버그] 판매중지·재고 0 상품(…010)이 담기고 결제까지 된다, 재고 차감도 없다")
    void discontinuedProduct_isSellable() throws Exception {
        String tranNo = newTranNo();

        assertThat(json(addItem(POS_1, tranNo, DISCONTINUED)).get("status").asInt()).isEqualTo(200);
        JsonNode sale = json(checkoutCash(POS_1, tranNo, 5000));

        assertThat(sale.get("saleStatus").asText()).isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("SELECT STOCK FROM INVENTORY WHERE PRODUCT_ID = 'P010'", Integer.class))
                .isEqualTo(0); // 재고 0 그대로 (음수로도 안 감 = 차감 자체가 없음)
    }

    @Test
    @DisplayName("[현재 동작] 결제해도 INVENTORY 재고는 차감되지 않는다")
    void checkout_doesNotDecreaseStock() throws Exception {
        String tranNo = newTranNo();
        Integer before = jdbc.queryForObject("SELECT STOCK FROM INVENTORY WHERE PRODUCT_ID = 'P003'", Integer.class);

        addItem(POS_1, tranNo, COOKIE);
        updateQty(POS_1, tranNo, COOKIE, 4);
        checkoutCash(POS_1, tranNo, 20000);

        assertThat(jdbc.queryForObject("SELECT STOCK FROM INVENTORY WHERE PRODUCT_ID = 'P003'", Integer.class))
                .isEqualTo(before);
    }

    @Test
    @DisplayName("[현재 동작/버그] 미등록 바코드: HTTP 200 + 바디 status 404, 카트 변화 없음")
    void unknownBarcode_returns200WithBody404() throws Exception {
        String tranNo = newTranNo();

        JsonNode res = json(addItem(POS_1, tranNo, UNKNOWN).andExpect(status().isOk()));

        assertThat(res.get("status").asInt()).isEqualTo(404);
        assertThat(res.get("message").asText()).isEqualTo("미등록 상품");
        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).isEmpty();
    }

    @Test
    @DisplayName("[현재 동작/버그] 일괄 등록에 미등록 바코드가 섞이면 실패 응답이지만 나머지는 이미 담겨 있다")
    void addMany_withUnknown_isPartiallyApplied() throws Exception {
        String tranNo = newTranNo();

        JsonNode res = json(addItems(POS_1, tranNo, CHIPS, UNKNOWN, CHOCO));

        assertThat(res.get("status").asInt()).isEqualTo(404);
        assertThat(json(getCart(POS_1, tranNo)).get("linesList"))
                .extracting(l -> l.get("barcode").asText())
                .containsExactly(CHIPS, CHOCO);
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
    @DisplayName("[현재 동작/버그] 수량 변경 요청에 qty 를 빼면 @Builder.Default 가 적용되지 않아 NPE(500)")
    void updateQty_withoutQtyField_throwsNpe() {
        String tranNo = newTranNo();

        assertThatThrownBy(() -> perform(post("/api/cart/item/update/qty"), with(key(POS_1, tranNo), "barcode", CHIPS)))
                .hasRootCauseInstanceOf(NullPointerException.class);
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
    @DisplayName("[현재 동작/버그] 같은 tranNo 를 다른 posNo 에서 쓰면 장바구니가 섞이고, 결제는 먼저 만든 POS 번호로 저장된다")
    void sameTranNo_differentPos_sharesCart() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);
        addItem(POS_2, tranNo, CHOCO);

        JsonNode pos2Cart = json(getCart(POS_2, tranNo));
        assertThat(pos2Cart.get("posNo").asText()).isEqualTo(POS_1); // POS 02 로 조회했는데 01 카트
        assertThat(pos2Cart.get("linesList"))
                .extracting(l -> l.get("barcode").asText())
                .containsExactly(CHIPS, CHOCO);

        JsonNode sale = json(checkoutCash(POS_2, tranNo, 5000));
        assertThat(sale.get("posNo").asText()).isEqualTo(POS_1);
        assertThat(countSales(POS_1, tranNo)).isEqualTo(1);
        assertThat(countSales(POS_2, tranNo)).isZero();

        // POS 01 의 장바구니도 함께 비워짐
        assertThat(json(getCart(POS_1, tranNo)).get("linesList")).isEmpty();
    }

    @Test
    @DisplayName("[현재 동작/버그] 같은 tranNo 를 다른 점포(strCd)에서 써도 장바구니가 섞인다")
    void sameTranNo_differentStore_sharesCart() throws Exception {
        String tranNo = newTranNo();
        addItem(POS_1, tranNo, CHIPS);

        var otherStore = with(with(key(POS_1, tranNo), "strCd", "S99"), "barcode", CHOCO);
        perform(post("/api/cart/item/add"), otherStore);

        JsonNode cart = json(getCart(POS_1, tranNo));
        assertThat(cart.get("strCd").asText()).isEqualTo(STORE);
        assertThat(cart.get("linesList")).hasSize(2);
    }
}
