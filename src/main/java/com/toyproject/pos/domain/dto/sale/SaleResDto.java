package com.toyproject.pos.domain.dto.sale;

import com.toyproject.pos.domain.dto.cart.CartSummaryResDto;
import com.toyproject.pos.domain.dto.payment.PaymentResDto;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.domain.model.sale.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class SaleResDto {

    private final String receiptNo;
    private final String storeCd;
    private final String posNo;
    private final String tranNo;
    private final String cashier;
    private final SaleStatus saleStatus;         // "NORMAL" 등
    private final String approvedAt;        // 결제 확정 시각
    private final CartSummaryResDto cart;  // 장바구니 스냅샷
    private final PaymentResDto payment;   // 결제 결과(수단별)
    private final Long origSaleId;

    public static SaleResDto of(Sale sale) {
        return SaleResDto.builder()
                .receiptNo(sale.getReceiptNo())
                .storeCd(sale.getStoreCd())
                .posNo(sale.getPosNo())
                .tranNo(sale.getTranNo())
                .cashier(sale.getCashier())
                .saleStatus(sale.getSaleStatus())
                .approvedAt(sale.getPayment().approvedAt())
                .cart(CartSummaryResDto.of(sale.getCartSummary()))
                .payment(PaymentResDto.of(sale.getPayment()))
                .origSaleId(getOrigSaleId(sale))
                .build()
                ;

    }

    private static Long getOrigSaleId(Sale sale) {
        return sale.getRefundDetail() != null
                ? sale.getRefundDetail().getOrigSaleId()
                : null;
    }

    public SaleResDto withOriginSaleId(Long saleId) {
        return this.toBuilder()
                .origSaleId(saleId)
                .build();
    }

}

