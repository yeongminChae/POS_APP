package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 컨트롤러 API 통합 테스트 공통 지원.
 *
 * <p>장바구니는 싱글톤 빈의 인메모리 맵이고 테스트 DB 파일도 한 번의 Gradle 실행 동안 공유되므로,
 * 테스트끼리 섞이지 않도록 {@link #newTranNo()} 로 매번 고유한 거래번호를 쓴다.
 * 결제 후 커밋 시점(afterCommit) 동작을 그대로 보기 위해 테스트에 {@code @Transactional} 을 붙이지 않는다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class PosApiTestSupport {

    static final String STORE = "S01";
    static final String POS_1 = "01";
    static final String POS_2 = "02";

    // 시드 바코드 (src/test/resources/db/test-data.sql)
    static final String CHIPS = "8800000000001";      // 1500
    static final String CHOCO = "8800000000002";      // 1200
    static final String COOKIE = "8800000000003";     // 2500
    static final String ADULT_SOJU = "8800000000009"; // 1950, 성인 전용
    static final String DISCONTINUED = "8800000000010"; // 2800, 판매중지, 재고 0
    static final String UNKNOWN = "8800000009999";

    private static final AtomicInteger TRAN_SEQ = new AtomicInteger(1000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    static String newTranNo() {
        return String.valueOf(TRAN_SEQ.incrementAndGet());
    }

    // ===== 요청 바디 =====

    static Map<String, Object> key(String posNo, String tranNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("strCd", STORE);
        m.put("posNo", posNo);
        m.put("tranNo", tranNo);
        return m;
    }

    static Map<String, Object> with(Map<String, Object> base, String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>(base);
        m.put(k, v);
        return m;
    }

    // ===== API 호출 (컨트롤러 시그니처 그대로: GET 에도 JSON 바디) =====

    ResultActions getCart(String posNo, String tranNo) throws Exception {
        return perform(get("/api/cart/item"), key(posNo, tranNo));
    }

    ResultActions addItem(String posNo, String tranNo, String barcode) throws Exception {
        return perform(post("/api/cart/item/add"), with(key(posNo, tranNo), "barcode", barcode));
    }

    ResultActions addItems(String posNo, String tranNo, String... barcodes) throws Exception {
        Object[] body = new Object[barcodes.length];
        for (int i = 0; i < barcodes.length; i++) body[i] = with(key(posNo, tranNo), "barcode", barcodes[i]);
        return perform(post("/api/cart/item/add/many"), body);
    }

    ResultActions updateQty(String posNo, String tranNo, String barcode, int qty) throws Exception {
        return perform(post("/api/cart/item/update/qty"),
                with(with(key(posNo, tranNo), "barcode", barcode), "qty", qty));
    }

    ResultActions cancelItem(String posNo, String tranNo, String barcode) throws Exception {
        return perform(post("/api/cart/item/cancel"), with(key(posNo, tranNo), "barcode", barcode));
    }

    ResultActions checkoutCash(String posNo, String tranNo, long amountReceived) throws Exception {
        Map<String, Object> body = with(with(key(posNo, tranNo), "paymentMethod", "CASH"), "amountReceived", amountReceived);
        return perform(post("/api/checkout"), body);
    }

    ResultActions callTransaction(String posNo, String tranNo) throws Exception {
        return perform(get("/api/checkout/transaction/call"), key(posNo, tranNo));
    }

    ResultActions refund(String newPosNo, String newTranNo, String origPosNo, String origTranNo) throws Exception {
        Map<String, Object> body = key(newPosNo, newTranNo);
        body.put("origStrCd", STORE);
        body.put("origPosNo", origPosNo);
        body.put("origTranNo", origTranNo);
        body.put("reason", "테스트 반품");
        return perform(post("/api/checkout/transaction/refund"), body);
    }

    ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return mvc.perform(builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** 금액 비교: 응답/DB 금액은 1500, 1500.0 처럼 스케일이 섞여 나오므로 값으로만 비교한다. */
    static java.math.BigDecimal amt(Object value) {
        if (value instanceof JsonNode node) return node.decimalValue();
        return new java.math.BigDecimal(String.valueOf(value));
    }

    // ===== DB 조회 =====

    Map<String, Object> saleRow(String posNo, String tranNo) {
        return jdbc.queryForMap("SELECT * FROM SALES WHERE STORE_CD = ? AND POS_NO = ? AND TRAN_NO = ?",
                STORE, posNo, tranNo);
    }

    long saleId(String posNo, String tranNo) {
        return ((Number) saleRow(posNo, tranNo).get("SALE_ID")).longValue();
    }

    int countSales(String posNo, String tranNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM SALES WHERE STORE_CD = ? AND POS_NO = ? AND TRAN_NO = ?",
                Integer.class, STORE, posNo, tranNo);
    }
}
