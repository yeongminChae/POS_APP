package com.toyproject.pos.domain.model.sale;

import com.toyproject.pos.common.util.DateFormatUtil;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.ZoneId;

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
        return create(cartSummary, payment, SaleStatus.NORMAL, null);
    }

    public static Sale refundedOf(
            CartSummary cartSummary,
            Payment payment,
            RefundDetail refundDetail
    ) {
        return create(cartSummary, payment, SaleStatus.REFUNDED, refundDetail);
    }

    private static Sale create(
            CartSummary cartSummary,
            Payment payment,
            SaleStatus saleStatus,
            RefundDetail refundDetail
    ) {
        return Sale.builder()
                .saleId(null)
                .receiptNo(getReceiptNo(cartSummary))
                .storeCd(cartSummary.getStrCd())
                .posNo(cartSummary.getPosNo())
                .tranNo(cartSummary.getTranNo())
                .cashier("테스터")
                .saleStatus(saleStatus)
                .cartSummary(cartSummary)
                .payment(payment)
                .refundDetail(refundDetail)
                .build()
                ;
    }

    private static String getReceiptNo(CartSummary cartSummary) {
        return cartSummary.getStrCd()
                + "-" + DateFormatUtil.nowDate(ZoneId.systemDefault())
                + "-" + cartSummary.getPosNo()
                + "-" + cartSummary.getTranNo()
                ;
    }

}
