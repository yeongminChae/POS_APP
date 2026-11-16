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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    // API 경로 (자원 단위, api-change-plan 2.3절)
    static final String CARTS = "/api/carts";
    static final String CART_SUMMARY = "/api/carts/summary";
    static final String CART_LINES = "/api/carts/lines";
    static final String CART_LINES_BATCH = "/api/carts/lines/batch";
    static final String CART_LINES_CANCEL = "/api/carts/lines/cancel";
    static final String SALES = "/api/sales";
    static final String SALE_REFUNDS = "/api/sales/refunds";

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

    // ===== API 호출 (GET 은 쿼리 파라미터, 나머지는 JSON 바디) =====

    ResultActions getCart(String posNo, String tranNo) throws Exception {
        return query(CARTS, posNo, tranNo);
    }

    ResultActions getSummary(String posNo, String tranNo) throws Exception {
        return query(CART_SUMMARY, posNo, tranNo);
    }

    ResultActions addItem(String posNo, String tranNo, String barcode) throws Exception {
        return perform(post(CART_LINES), with(key(posNo, tranNo), "barcode", barcode));
    }

    ResultActions addItems(String posNo, String tranNo, String... barcodes) throws Exception {
        Object[] body = new Object[barcodes.length];
        for (int i = 0; i < barcodes.length; i++) body[i] = with(key(posNo, tranNo), "barcode", barcodes[i]);
        return perform(post(CART_LINES_BATCH), body);
    }

    ResultActions updateQty(String posNo, String tranNo, String barcode, int qty) throws Exception {
        return perform(patch(CART_LINES),
                with(with(key(posNo, tranNo), "barcode", barcode), "qty", qty));
    }

    ResultActions cancelItem(String posNo, String tranNo, String barcode) throws Exception {
        return perform(post(CART_LINES_CANCEL), with(key(posNo, tranNo), "barcode", barcode));
    }

    ResultActions checkoutCash(String posNo, String tranNo, long amountReceived) throws Exception {
        Map<String, Object> body = with(with(key(posNo, tranNo), "paymentMethod", "CASH"), "amountReceived", amountReceived);
        return perform(post(SALES), body);
    }

    ResultActions callTransaction(String posNo, String tranNo) throws Exception {
        return query(SALES, posNo, tranNo);
    }

    ResultActions refund(String newPosNo, String newTranNo, String origPosNo, String origTranNo) throws Exception {
        Map<String, Object> body = key(newPosNo, newTranNo);
        body.put("origStrCd", STORE);
        body.put("origPosNo", origPosNo);
        body.put("origTranNo", origTranNo);
        body.put("reason", "테스트 반품");
        return perform(post(SALE_REFUNDS), body);
    }

    /** 거래 키를 쿼리 파라미터로 보내는 GET */
    ResultActions query(String path, String posNo, String tranNo) throws Exception {
        return mvc.perform(get(path).param("strCd", STORE).param("posNo", posNo).param("tranNo", tranNo));
    }

    ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return mvc.perform(builder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * 오류 응답 검사: HTTP 상태, ProblemDetail 형식, 오류 코드. 바디(JsonNode)를 돌려줘서 detail 등을 더 검사할 수 있다.
     */
    JsonNode error(ResultActions result, int status, String code) throws Exception {
        var response = result.andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo(code);
        return body;
    }

    /** 성공 응답 검사: HTTP 200 이고 바디(JsonNode)를 돌려준다 */
    JsonNode ok(ResultActions result) throws Exception {
        assertThat(result.andReturn().getResponse().getStatus()).isEqualTo(200);
        return json(result);
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

    int stock(String productId) {
        return jdbc.queryForObject("SELECT STOCK FROM INVENTORY WHERE PRODUCT_ID = ?", Integer.class, productId);
    }

    int countSales(String posNo, String tranNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM SALES WHERE STORE_CD = ? AND POS_NO = ? AND TRAN_NO = ?",
                Integer.class, STORE, posNo, tranNo);
    }
}
