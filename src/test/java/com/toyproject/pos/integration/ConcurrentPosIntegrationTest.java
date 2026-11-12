package com.toyproject.pos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

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
 * <p>장바구니 담기는 인메모리 맵이 스레드에 안전하지 않아(N5) 동시에 실행하지 않고, 출발 전에 순서대로 담는다.</p>
 */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
class ConcurrentPosIntegrationTest extends PosApiTestSupport {

    private static final int ROUNDS = 10;

    @Test
    @DisplayName("POS 01 결제와 POS 02 반품을 동시에 실행해도 모두 성공하고 결제 상세가 올바른 결제에 붙는다")
    void checkoutAndRefund_concurrently() throws Exception {
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
                Future<Throwable> a = pool.submit(awaiting(start, () -> failureOf(() -> refund(POS_1, refund1, POS_1, original))));
                Future<Throwable> b = pool.submit(awaiting(start, () -> failureOf(() -> refund(POS_2, refund2, POS_1, original))));
                start.countDown();

                List<Throwable> failures = new ArrayList<>();
                for (Future<Throwable> f : List.of(a, b)) {
                    Throwable t = f.get(30, TimeUnit.SECONDS);
                    if (t != null) failures.add(t);
                }

                assertThat(failures).hasSize(1);
                assertThat(rootCause(failures.get(0)))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("이미 반품된 거래입니다.");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SALES WHERE ORIG_SALE_ID = ?", Integer.class,
                        saleId(POS_1, original))).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Throwable failureOf(Callable<?> call) {
        try {
            call.call();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t;
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
