package com.toyproject.pos.persistence.mapper;

import com.toyproject.pos.persistence.row.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CheckoutMapper {
    // INSERTs
    int insertSale(SaleRow sale);  // useGeneratedKeys로 SALE_ID 채움

    int insertSaleLines(@Param("lines") List<SaleLineRow> lines);

    int insertPayment(PaymentRow payment);

    int insertCashPayment(CashPaymentRow cash);

    int insertJournalData(JournalDataRow journal);

    // SELECTs (constructor resultMap 사용)
    SaleRow selectSaleByTranInfo(@Param("storeCd") String storeCd,
                                 @Param("posNo") String posNo,
                                 @Param("tranNo") String tranNo);

//    List<SaleLineRow> selectSaleLinesBySaleId(@Param("saleId") Long saleId);

//    PaymentRow selectPaymentBySaleId(@Param("saleId") Long saleId);

    List<SaleLineRow> selectSaleLinesBySaleKey(@Param("storeCd") String storeCd,
                                               @Param("posNo") String posNo,
                                               @Param("tranNo") String tranNo);

    PaymentRow selectPaymentBySaleKey(@Param("storeCd") String storeCd,
                                      @Param("posNo") String posNo,
                                      @Param("tranNo") String tranNo);

    CashPaymentRow selectCashPaymentById(@Param("paymentId") Long paymentId);

}
