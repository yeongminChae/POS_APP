package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.domain.model.sale.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class SaleRow {
    private final Long saleId;           // AUTO (INSERT 시 null)
    private final String receiptNo;
    private final String storeCd;
    private final String posNo;
    private final String tranNo;
    private final String cashier;
    private final SaleStatus saleStatus;     // "NORMAL","REFUNDED"
    private final String approvedAt;     // ISO8601 (Instant.toString)
    private final Integer totalQty;
    private final BigDecimal totalAmount;
    private final Long origSaleId;       // 환불 시 원거래 참조

    // 환불 거래 저장 시
    public static SaleRow of(Sale sale) {
        CartSummary cartSummary = sale.getCartSummary();
        Payment payment = sale.getPayment();

        return SaleRow.builder()
                .saleId(null)
                .receiptNo(sale.getReceiptNo())
                .storeCd(sale.getStoreCd())
                .posNo(sale.getPosNo())
                .tranNo(sale.getTranNo())
                .cashier(sale.getCashier())
                .saleStatus(sale.getSaleStatus())
                .approvedAt(payment.approvedAt())
                .totalQty(cartSummary.getTotalQty())
                .totalAmount(cartSummary.getTotalAmount())
                .origSaleId(getOrigSaleId(sale))
                .build();

    }

    // origSaleId가 null 이면 정상 거래, 값이 있으면 반품 거래
    private static Long getOrigSaleId(Sale sale) {
        return sale.getRefundDetail() != null
                ? sale.getRefundDetail().getOrigSaleId()
                : null
                ;
    }

}
