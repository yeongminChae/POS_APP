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

@Repository
@RequiredArgsConstructor
public class CheckoutRepositoryImpl implements CheckoutRepository {

    private final CheckoutMapper checkoutMapper;
    private final ObjectMapper objectMapper;

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
                    throw new UnsupportedOperationException("CARD 결제는 아직 미지원");
            }

        }

        return saleId;

    }

    @Override
    public Sale getTransaction(CartKey cartKey, CartSummary cartSummary, Payment payment) {

        SaleRow saleRow = getSaleRow(cartKey);

        return SaleRowMapper.toSale(saleRow, cartSummary, payment);
    }

    @Override
    public Cart getCart(CartKey cartKey) {

        List<SaleLineRow> saleLineRows = checkoutMapper.selectSaleLinesBySaleKey(
                cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo()
        );

        return SaleRowMapper.getCartTransaction(cartKey, saleLineRows);
    }

    @Override
    public Payment getPayment(CartKey cartKey) {

        PaymentRow paymentRow = checkoutMapper.selectPaymentBySaleKey(
                cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo()
        );

        Long paymentId = paymentRow.getPaymentId();
        CashPaymentRow cashPaymentRow = checkoutMapper.selectCashPaymentById(paymentId);

        return SaleRowMapper.getPaymentSummary(cartKey, paymentRow, cashPaymentRow);
    }

    private SaleRow getSaleRow(CartKey cartKey) {
        return checkoutMapper.selectSaleByTranInfo(
                cartKey.getStrCd(), cartKey.getPosNo(), cartKey.getTranNo()
        );
    }

    private List<SaleLineRow> getSaleLineRows(Sale sale, Long saleId) {
        List<SaleLineRow> rows = new ArrayList<>();

        for (var line : sale.getCartSummary().getLines())
            rows.add(SaleLineRow.of(line, saleId));

        return rows;

    }

    private JournalDataRow getJournalDataRow(Sale sale, Long saleId) {
        String trJson = convertTrData(sale); // ObjectMapper 사용

        return JournalDataRow.of(sale, saleId, trJson);
    }

    private String convertTrData(Sale sale) {
        try {
            return objectMapper.writeValueAsString(sale);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

}
