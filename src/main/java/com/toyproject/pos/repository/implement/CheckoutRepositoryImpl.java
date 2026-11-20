package com.toyproject.pos.repository.implement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.persistence.mapper.CheckoutMapper;
import com.toyproject.pos.persistence.mapping.SaleRowMapper;
import com.toyproject.pos.persistence.row.*;
import com.toyproject.pos.repository.interfaces.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 판매 저장소 — 판매 도메인({@link Sale})을 DB 테이블 행(Row)으로 나눠 저장하고, 거래 키로 다시 읽어 온다.
 *
 * <ul>
 *   <li>저장: 도메인 → {@code XxxRow.of(...)} → MyBatis 매퍼 ({@code CheckoutMapper.xml})</li>
 *   <li>조회: 매퍼 → Row → {@link SaleRowMapper}가 도메인으로 조립</li>
 * </ul>
 * 트랜잭션은 호출하는 서비스({@code CheckoutServiceImpl})가 연다. 여기서 실패하면 결제·반품 전체가 롤백된다.
 */
@Repository
@RequiredArgsConstructor
public class CheckoutRepositoryImpl implements CheckoutRepository {

    private final CheckoutMapper checkoutMapper;  // MyBatis 매퍼
    private final ObjectMapper objectMapper;      // 영수증 저널(TR_JSON)용 JSON 직렬화

    /**
     * 판매 한 건을 5개 테이블에 순서대로 저장한다. 앞 단계에서 DB 가 만든 ID 를 다음 단계가 외래키로 쓴다.
     * <ol>
     *   <li>SALES — 판매 머리 (생성된 SALE_ID 를 받음)</li>
     *   <li>SALE_LINES — 라인 전부 (SALE_ID 참조)</li>
     *   <li>PAYMENTS — 결제 공통 (생성된 PAYMENT_ID 를 받음)</li>
     *   <li>현금이면 CASH_PAYMENTS(받은 금액·거스름돈, PAYMENT_ID 참조) + RECEIPT_JOURNAL(판매 전체 JSON)</li>
     * </ol>
     *
     * @return 생성된 SALE_ID
     */
    @Override
    public Long save(Sale sale) {
        // 1) SALES
        SaleRow saleRow = SaleRow.of(sale);
        checkoutMapper.insertSale(saleRow); // generated SALE_ID 채워짐

        Long saleId = saleRow.getSaleId();

        // 2) SALE_LINES
        List<SaleLineRow> rows = getSaleLineRows(sale, saleId);
        if (rows.isEmpty() == false) checkoutMapper.insertSaleLines(rows);

        // 3) PAYMENT
        Payment payment = sale.getPayment();
        if (payment != null) {
            PaymentRow paymentRow = PaymentRow.of(payment, saleId);
            checkoutMapper.insertPayment(paymentRow); // generated PAYMENT_ID 채워짐

            switch (payment.method()) {

                case CASH:
                    checkoutMapper.insertCashPayment(CashPaymentRow.of(payment, paymentRow.getPaymentId()));
                    checkoutMapper.insertJournalData(getJournalDataRow(sale, saleId));

                    break;

                case CARD:
                    // 도달하지 않음: 카드 결제는 서비스에서 먼저 400 으로 거절된다 (카드 구현 시 저장 정책 정할 곳 — 보류 L5)
                    throw new UnsupportedOperationException("CARD 결제는 아직 미지원");
            }

        }

        return saleId;

    }

    /**
     * 원거래를 반품 상태로 바꾼다: {@code UPDATE SALES SET SALE_STATUS='REFUNDED' WHERE SALE_ID=? AND SALE_STATUS='NORMAL'}.
     * 조건부 UPDATE 한 번이라 동시에 두 반품이 와도 한 건만 1행을 바꾼다.
     *
     * @return 바뀐 행이 1개면 true, 이미 반품된 거래면 false
     */
    @Override
    public boolean markRefunded(Long saleId) {
        return checkoutMapper.markSaleRefunded(saleId) == 1;
    }

    /**
     * 거래 키의 SALES 행을 읽어, 이미 복원한 장바구니 요약·결제와 합쳐 {@link Sale}로 조립한다.
     * 호출 전에 {@link #existsTransaction}으로 존재를 확인해야 한다.
     */
    @Override
    public Sale getTransaction(CartKey cartKey, CartSummary cartSummary, Payment payment) {

        SaleRow saleRow = getSaleRow(cartKey);

        return SaleRowMapper.toSale(saleRow, cartSummary, payment);
    }

    /** 거래 키로 저장된 판매가 있는지 (결제 중복 확인, 조회·반품 전 존재 확인에 사용) */
    @Override
    public boolean existsTransaction(CartKey cartKey) {
        return getSaleRow(cartKey) != null;
    }

    /** 거래 키의 SALE_LINES 를 읽어 장바구니로 복원한다 (조회·반품용, 라인 번호 순). */
    @Override
    public Cart getCart(CartKey cartKey) {

        List<SaleLineRow> saleLineRows = checkoutMapper.selectSaleLinesBySaleKey(
                cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo()
        );

        return SaleRowMapper.getCartTransaction(cartKey, saleLineRows);
    }

    /** 거래 키의 PAYMENTS 행과, 그 PAYMENT_ID 의 CASH_PAYMENTS 행을 읽어 결제로 복원한다. */
    @Override
    public Payment getPayment(CartKey cartKey) {

        PaymentRow paymentRow = checkoutMapper.selectPaymentBySaleKey(
                cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo()
        );

        Long paymentId = paymentRow.getPaymentId();
        CashPaymentRow cashPaymentRow = checkoutMapper.selectCashPaymentById(paymentId);

        return SaleRowMapper.getPaymentSummary(cartKey, paymentRow, cashPaymentRow);
    }

    /** 거래 키로 SALES 행 조회 (없으면 null) */
    private SaleRow getSaleRow(CartKey cartKey) {
        return checkoutMapper.selectSaleByTranInfo(
                cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo()
        );
    }

    /** 판매의 모든 라인(취소·반품 포함)을 SALE_LINES 행으로 바꾼다. */
    private List<SaleLineRow> getSaleLineRows(Sale sale, Long saleId) {
        List<SaleLineRow> rows = new ArrayList<>();

        for (var line : sale.getCartSummary().getLines())
            rows.add(SaleLineRow.of(line, saleId));

        return rows;

    }

    /** 영수증 저널 행: 판매 전체를 JSON 으로 남긴다 (반품 사유처럼 컬럼이 없는 값도 여기에만 저장된다). */
    private JournalDataRow getJournalDataRow(Sale sale, Long saleId) {
        // 저장 후 생성된 SALE_ID 를 채운 Sale 을 직렬화 (ObjectMapper 사용)
        String trJson = convertTrData(sale.toBuilder().saleId(saleId).build());

        return JournalDataRow.of(sale, saleId, trJson);
    }

    /** 판매를 JSON 문자열로 직렬화. 실패하면 RuntimeException(500)으로 결제가 롤백된다. */
    private String convertTrData(Sale sale) {
        try {
            return objectMapper.writeValueAsString(sale);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

}
