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

/**
 * DB 행(Row) → 도메인 모델 조립기 (조회 방향 전용, 정적 메서드만 있음).
 *
 * <p>거래 조회·반품 시 저장소({@code CheckoutRepositoryImpl})가 사용한다. 복원은 저장된 값을 그대로 쓰고
 * 생성 시의 검증·계산을 다시 하지 않는다 (예: 반품 결제의 음수 금액, 저장된 합계).
 * 저장 방향(도메인 → Row)은 각 Row 클래스의 {@code of(...)}가 맡는다.</p>
 */
public final class SaleRowMapper {

    // 거래 호출 시, 로우 -> Sale 도메인 매핑
    /**
     * SALES 행 + 복원한 장바구니 요약·결제 → {@link Sale}.
     * 반품 거래면 원거래 ID 를 담은 반품 상세도 채운다.
     */
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

    /** SALE_LINES 행들 → {@link Cart}. 라인 번호를 lineId 로 써서 저장 당시 순서·번호를 그대로 살린다. */
    public static Cart getCartTransaction(CartKey cartKey, List<SaleLineRow> lineRows) {
        return Cart.of(cartKey, getLineMap(lineRows));
    }

    /**
     * PAYMENTS 행(+ CASH_PAYMENTS 행) → {@link Payment}. 결제 수단에 따라 구현체를 고른다.
     * 카드는 미구현이라 null 을 돌려준다 (카드 결제는 저장 자체가 막혀 있어 실제로는 도달하지 않음).
     *
     * @param cashRow 현금 상세 (현금이 아니면 null)
     */
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

    /** PAYMENTS 행 → 결제 공통 정보 */
    private static CommonPaymentData getCommonPaymentData(CartKey cartKey, PaymentRow paymentRow) {
        return CommonPaymentData.restoredOf(
                paymentRow.getPaymentId(),
                cartKey,
                paymentRow.getAmountDueToPay(),
                paymentRow.getMethod(),
                paymentRow.getApprovedAt()
        );
    }

    /** 라인 번호 → 라인 맵 (조회 순서 = 라인 번호 순 유지) */
    private static Map<String, CartLine> getLineMap(List<SaleLineRow> lineRows) {
        Map<String, CartLine> lineMap = new LinkedHashMap<>();

        for (SaleLineRow saleLineRow : lineRows)
            lineMap.put(saleLineRow.getLineNo(), getCartLine(saleLineRow));

        return lineMap;
    }

    /** SALE_LINES 행 1개 → 장바구니 라인. 상품 ID 는 SALE_LINES 에 없어서 null (재고 반영은 바코드로 하므로 문제없음) */
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
