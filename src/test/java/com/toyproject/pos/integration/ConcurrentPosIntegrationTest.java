package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점포 1개에 POS 여러 대가 같은 DB 파일에 동시에 쓰는 시나리오.
 *
 * <p>테스트 프로필은 커넥션 풀이 1개라 요청이 모두 직렬화되므로, 이 클래스만 풀을 4개로 늘린다.
 * (별도 Spring 컨텍스트가 뜨면서 시드가 다시 실행되지만, 시드는 맨 앞에서 전체 DELETE 하므로 안전하다.)</p>
 *
 * <p>결제·반품 시나리오는 DB 동시성을 보려는 것이라 장바구니 담기는 출발 전에 순서대로 한다.
 * 담기 자체의 동시성은 {@link #addItems_concurrently_onManyCarts()} 에서 따로 확인한다.</p>
 */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class ConcurrentPosIntegrationTest extends PosApiTestSupport {

    private static final int ROUNDS = 10;

    @Test
    @DisplayName("POS 01 결제와 POS 02 반품을 동시에 실행해도 모두 성공하고 결제 상세가 올바른 결제에 붙는다")
    void checkoutAndRefund_concurrently() throws Exception {
        int chipsBefore = stock("P001");
        int cookieBefore = stock("P003");

        // 반품할 원거래를 라운드 수만큼 미리 만든다 (POS 02)
        List<String> originals = new ArrayList<>();
        for (int i = 0; i < ROUNDS; i++) {
            String tranNo = newTranNo();
            addItem(POS_2, tranNo, COOKIE);
            checkoutCash(POS_2, tranNo, 5000);
            originals.add(tranNo);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < ROUNDS; i++) {
                String saleTranNo = newTranNo();
                String refundTranNo = newTranNo();
                String original = originals.get(i);
                addItem(POS_1, saleTranNo, CHIPS);

                CountDownLatch start = new CountDownLatch(1);
                Future<JsonNode> sale = pool.submit(awaiting(start, () -> json(checkoutCash(POS_1, saleTranNo, 2000))));
                Future<JsonNode> refund = pool.submit(awaiting(start, () -> json(refund(POS_2, refundTranNo, POS_2, original))));
                start.countDown();

                assertThat(sale.get(30, TimeUnit.SECONDS).get("saleStatus").asText()).isEqualTo("NORMAL");
                assertThat(refund.get(30, TimeUnit.SECONDS).get("saleStatus").asText()).isEqualTo("REFUNDED");

                assertThat(countSales(POS_1, saleTranNo)).isEqualTo(1);
                assertThat(((Number) saleRow(POS_2, refundTranNo).get("ORIG_SALE_ID")).longValue())
                        .isEqualTo(saleId(POS_2, original));
                assertCashDetailMatches(saleId(POS_1, saleTranNo), "2000");
                assertCashDetailMatches(saleId(POS_2, refundTranNo), null);
            }
        } finally {
            pool.shutdownNow();
        }

        // 동시 결제·반품 후 재고: 감자칩은 결제한 만큼 줄고, 쿠키는 결제 후 전부 반품되어 원래대로
        assertThat(stock("P001")).isEqualTo(chipsBefore - ROUNDS);
        assertThat(stock("P003")).isEqualTo(cookieBefore);
    }

    @Test
    @DisplayName("POS 2대가 같은 원거래를 동시에 반품하면 정확히 1건만 성공한다")
    void sameOriginal_refundedConcurrently_onlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < ROUNDS; i++) {
                String original = newTranNo();
                addItem(POS_1, original, CHIPS);
                checkoutCash(POS_1, original, 2000);
                String refund1 = newTranNo();
                String refund2 = newTranNo();

                CountDownLatch start = new CountDownLatch(1);
                Future<MvcResult> a = pool.submit(awaiting(start, () -> refund(POS_1, refund1, POS_1, original).andReturn()));
                Future<MvcResult> b = pool.submit(awaiting(start, () -> refund(POS_2, refund2, POS_1, original).andReturn()));
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                MvcResult rejected = null;
                for (Future<MvcResult> f : List.of(a, b)) {
                    MvcResult r = f.get(30, TimeUnit.SECONDS);
                    statuses.add(r.getResponse().getStatus());
                    if (r.getResponse().getStatus() != 200) rejected = r;
                }

                assertThat(statuses).containsExactlyInAnyOrder(200, 409);
                assertThat(objectMapper.readTree(rejected.getResponse().getContentAsString()).get("code").asText())
                        .isEqualTo("ALREADY_REFUNDED");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SALES WHERE ORIG_SALE_ID = ?", Integer.class,
                        saleId(POS_1, original))).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("POS 여러 대가 서로 다른 거래에 동시에 담아도 모든 장바구니가 정확하다 (인메모리 저장소 동시성)")
    void addItems_concurrently_onManyCarts() throws Exception {
        int threads = 8;
        int cartsPerThread = 50; // 맵 크기가 여러 번 늘어나도록 많은 카트를 만든다

        List<List<String>> tranNos = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            List<String> mine = new ArrayList<>();
            for (int i = 0; i < cartsPerThread; i++) mine.add(newTranNo());
            tranNos.add(mine);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Integer>>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String posNo = String.format("%02d", t + 1);
                List<String> mine = tranNos.get(t);
                results.add(pool.submit(awaiting(start, () -> {
                    List<Integer> statuses = new ArrayList<>();
                    for (String tranNo : mine)
                        statuses.add(addItem(posNo, tranNo, CHIPS).andReturn().getResponse().getStatus());
                    return statuses;
                })));
            }
            start.countDown();

            for (Future<List<Integer>> r : results)
                assertThat(r.get(60, TimeUnit.SECONDS)).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }

        for (int t = 0; t < threads; t++) {
            String posNo = String.format("%02d", t + 1);
            for (String tranNo : tranNos.get(t)) {
                JsonNode lines = json(getCart(posNo, tranNo)).get("lines");
                assertThat(lines).hasSize(1);
                assertThat(lines.get(0).get("qty").asInt()).isEqualTo(1);
            }
        }
    }

    /** 결제 1건과 현금 상세 1건이 같은 PAYMENT_ID 로 연결되어 있는지 */
    private void assertCashDetailMatches(long saleId, String amountReceived) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.PAYMENT_ID, c.AMOUNT_RECEIVED FROM PAYMENTS p "
                        + "LEFT JOIN CASH_PAYMENTS c ON c.PAYMENT_ID = p.PAYMENT_ID WHERE p.SALE_ID = ?", saleId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("AMOUNT_RECEIVED")).isNotNull();
        if (amountReceived != null)
            assertThat(amt(rows.get(0).get("AMOUNT_RECEIVED"))).isEqualByComparingTo(amountReceived);
    }

    private static <T> Callable<T> awaiting(CountDownLatch start, Callable<T> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }
}
