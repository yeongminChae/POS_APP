package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.domain.model.sale.SaleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * {@code SALES} 테이블 1행 — 판매 머리 (거래 키·상태·합계·원거래 참조).
 * (STORE_CD, POS_NO, TRAN_NO)가 유일하다 — 같은 거래 키로는 한 번만 저장된다.
 * 합계는 저장 당시 값이고, 조회 시에도 라인에서 다시 계산하지 않고 이 값을 쓴다.
 */
@Getter
@Builder
@AllArgsConstructor
public class SaleRow {
    private final Long saleId;           // AUTO (INSERT 시 null)
    private final String receiptNo;      // 영수증 번호
    private final String storeCd;        // 점포 코드
    private final String posNo;          // POS 번호
    private final String tranNo;         // 거래번호
    private final String cashier;        // 계산원 (현재 "테스터" 고정)
    private final SaleStatus saleStatus;     // "NORMAL","REFUNDED"
    private final String approvedAt;     // 승인 시각 "yyyy-MM-dd HH:mm:ss" (Asia/Seoul, 결제 승인 시각)
    private final Integer totalQty;      // 총수량 (반품 거래는 음수)
    private final BigDecimal totalAmount; // 총금액 (반품 거래는 음수)
    private final Long origSaleId;       // 환불 시 원거래 참조

    // 환불 거래 저장 시
    /** 판매 도메인 → 행. 일반 판매와 반품 거래 모두 이 메서드로 저장한다 (반품이면 원거래 ID 가 채워짐). */
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
