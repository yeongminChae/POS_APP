package com.toyproject.pos.persistence.mapping;

import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.CommonPaymentData;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.RefundDetail;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.persistence.row.CashPaymentRow;
import com.toyproject.pos.persistence.row.PaymentRow;
import com.toyproject.pos.persistence.row.SaleLineRow;
import com.toyproject.pos.persistence.row.SaleRow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SaleRowMapper {

    // 거래 호출 시, 로우 -> Sale 도메인 매핑
    public static Sale toSale(
            SaleRow saleRow,
            CartSummary summary,
            Payment payment
    ) {

        return Sale.builder()
                .saleId(saleRow.getSaleId())
                .receiptNo(saleRow.getReceiptNo())
                .storeCd(saleRow.getStoreCd())
                .posNo(saleRow.getPosNo())
                .tranNo(saleRow.getTranNo())
                .cashier(saleRow.getCashier())
                .saleStatus(saleRow.getSaleStatus())
                // 합계는 저장값 사용 (반품 거래는 라인이 REFUNDED 라 NORMAL 기준으로 다시 합산하면 0이 됨)
                .cartSummary(CartSummary.restoredOf(summary, saleRow.getTotalQty(), saleRow.getTotalAmount()))
                .payment(payment)
                .refundDetail(getRefundDetail(saleRow))
                .build();
    }

    // 반품 거래면 원거래 ID만 복원 (나머지 반품 정보는 SALES 에 저장하지 않음)
    private static RefundDetail getRefundDetail(SaleRow saleRow) {
        return saleRow.getOrigSaleId() != null
                ? RefundDetail.restoredOf(saleRow.getOrigSaleId())
                : null;
    }

    public static Cart getCartTransaction(CartKey cartKey, List<SaleLineRow> lineRows) {
        return Cart.of(cartKey, getLineMap(lineRows));
    }

    public static Payment getPaymentSummary(
            CartKey cartKey,
            PaymentRow paymentRow,
            CashPaymentRow cashRow
    ) {

        switch (paymentRow.getMethod()) {
            case CASH:
                return CashPayment.restoredOf(
                        getCommonPaymentData(cartKey, paymentRow),
                        cashRow.getAmountReceived(),
                        cashRow.getChange(),
                        paymentRow.getStatus()
                );

            case CARD:
                break;
        }

        return null;

    }

    private static CommonPaymentData getCommonPaymentData(CartKey cartKey, PaymentRow paymentRow) {
        return CommonPaymentData.restoredOf(
                paymentRow.getPaymentId(),
                cartKey,
                paymentRow.getAmountDueToPay(),
                paymentRow.getMethod(),
                paymentRow.getApprovedAt()
        );
    }

    private static Map<String, CartLine> getLineMap(List<SaleLineRow> lineRows) {
        Map<String, CartLine> lineMap = new LinkedHashMap<>();

        for (SaleLineRow saleLineRow : lineRows)
            lineMap.put(saleLineRow.getLineNo(), getCartLine(saleLineRow));

        return lineMap;
    }

    public static CartLine getCartLine(SaleLineRow saleLineRow) {
        return CartLine.builder()
                .productId(null)                    // 저장 안 했다면 null 가능
                .lineId(saleLineRow.getLineNo())              // lineNo 주입
                .barcode(saleLineRow.getBarcode())
                .name(saleLineRow.getName())
                .unitPrice(saleLineRow.getUnitPrice())
                .qty(saleLineRow.getQty())
                .amount(saleLineRow.getAmount())
                .itemStatus(saleLineRow.getItemStatus())
                .build();
    }

}

