package com.toyproject.pos.domain.model.sale;

import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class Sale {

    private final Long saleId;
    private final String receiptNo;
    private final String storeCd;
    private final String posNo;
    private final String tranNo;
    private final String cashier;
    private final SaleStatus saleStatus;
    private final CartSummary cartSummary;
    private final Payment payment;
    private final RefundDetail refundDetail;

    public static Sale of(CartSummary cartSummary, Payment payment) {
        return Sale.builder()
                .saleId(null)
                .receiptNo(getReceiptNo(cartSummary))
                .storeCd(cartSummary.getStrCd())
                .posNo(cartSummary.getPosNo())
                .tranNo(cartSummary.getTranNo())
                .cashier("테스터")
                .saleStatus(SaleStatus.NORMAL)
                .cartSummary(cartSummary)
                .payment(payment)
                .refundDetail(null)
                .build()
                ;
    }

    public static Sale refundedOf(
            CartSummary cartSummary,
            Payment payment,
            RefundDetail refundDetail
    ) {
        return Sale.builder()
                .saleId(null)
                .receiptNo(getReceiptNo(cartSummary))
                .storeCd(cartSummary.getStrCd())
                .posNo(cartSummary.getPosNo())
                .tranNo(cartSummary.getTranNo())
                .cashier("테스터")
                .saleStatus(SaleStatus.REFUNDED)
                .cartSummary(cartSummary)
                .payment(payment)
                .refundDetail(refundDetail)
                .build();
    }

    private static String getReceiptNo(CartSummary cartSummary) {
        return cartSummary.getStrCd()
                + "-" + getDateFormat()
                + "-" + cartSummary.getPosNo()
                + "-" + cartSummary.getTranNo()
                ;
    }

    private static String getDateFormat() {
        return DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now())
                ;
    }

}
