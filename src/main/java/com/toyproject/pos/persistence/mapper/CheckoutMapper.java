package com.toyproject.pos.persistence.mapper;

import com.toyproject.pos.persistence.row.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 판매 관련 MyBatis 매퍼. SQL 은 {@code resources/mapper/CheckoutMapper.xml}에 있다.
 * 저장소({@code CheckoutRepositoryImpl})만 사용한다. 조회는 거래 키(점포·POS·거래번호)로 한다.
 */
@Mapper
public interface CheckoutMapper {
    // INSERTs
    /** SALES 1행 저장 */
    int insertSale(SaleRow sale);  // useGeneratedKeys로 SALE_ID 채움

    /** SALE_LINES 여러 행을 한 번에 저장 */
    int insertSaleLines(@Param("lines") List<SaleLineRow> lines);

    /** PAYMENTS 1행 저장 (useGeneratedKeys 로 PAYMENT_ID 를 객체에 채움) */
    int insertPayment(PaymentRow payment);

    /** CASH_PAYMENTS 1행 저장 (받은 금액·거스름돈) */
    int insertCashPayment(CashPaymentRow cash);

    /** RECEIPT_JOURNAL 1행 저장 (판매 전체 JSON) */
    int insertJournalData(JournalDataRow journal);

    // UPDATEs
    /** 원거래를 REFUNDED 로 표시 — 중복 반품 방지의 핵심 */
    int markSaleRefunded(@Param("saleId") Long saleId); // NORMAL 일 때만 갱신, 영향 행 수 반환

    // SELECTs (constructor resultMap 사용)
    /** 거래 키로 SALES 1행 조회 (없으면 null) */
    SaleRow selectSaleByTranInfo(@Param("storeCd") String storeCd,
                                 @Param("posNo") String posNo,
                                 @Param("tranNo") String tranNo);

    /** 거래 키의 SALE_LINES 를 라인 번호 순으로 조회 */
    List<SaleLineRow> selectSaleLinesBySaleKey(@Param("storeCd") String storeCd,
                                               @Param("posNo") String posNo,
                                               @Param("tranNo") String tranNo);

    /** 거래 키의 PAYMENTS 조회 (여러 건이면 승인 시각이 가장 늦은 1건) */
    PaymentRow selectPaymentBySaleKey(@Param("storeCd") String storeCd,
                                      @Param("posNo") String posNo,
                                      @Param("tranNo") String tranNo);

    /** PAYMENT_ID 로 CASH_PAYMENTS 조회 (현금이 아니면 null) */
    CashPaymentRow selectCashPaymentById(@Param("paymentId") Long paymentId);

}
