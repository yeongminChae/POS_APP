package com.toyproject.pos.domain.dto.sale;

import com.toyproject.pos.domain.dto.cart.CartSummaryResDto;
import com.toyproject.pos.domain.dto.payment.PaymentResDto;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.domain.model.sale.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 판매 응답 DTO — 결제({@code POST /api/sales}), 거래 조회({@code GET /api/sales}), 반품({@code POST /api/sales/refunds})의 200 응답.
 *
 * <p>반품 거래면 {@code origSaleId}에 원거래 SALE_ID 가 들어가고, 장바구니·결제 금액이 음수다.
 * 참고: 점포 코드 키 이름이 여기서는 {@code storeCd}, 장바구니 쪽은 {@code strCd}로 다르다 (기존 계약 유지).</p>
 */
@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class SaleResDto {

    private final String receiptNo;              // 영수증 번호
    private final String storeCd;                // 점포 코드
    private final String posNo;                  // POS 번호
    private final String tranNo;                 // 거래번호
    private final String cashier;                // 계산원 (현재 "테스터" 고정)
    private final SaleStatus saleStatus;         // "NORMAL" 등
    private final String approvedAt;        // 결제 확정 시각
    private final CartSummaryResDto cart;  // 장바구니 스냅샷
    private final PaymentResDto payment;   // 결제 결과(수단별)
    private final Long origSaleId;               // 반품 거래의 원거래 SALE_ID (일반 판매는 null)

    /** 판매 도메인 → 응답 */
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

    /** 반품 상세가 있으면 원거래 ID, 없으면 null */
    private static Long getOrigSaleId(Sale sale) {
        return sale.getRefundDetail() != null
                ? sale.getRefundDetail().getOrigSaleId()
                : null;
    }

}
