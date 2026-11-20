package com.toyproject.pos.domain.model.sale;

import com.toyproject.pos.common.util.DateFormatUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 반품 상세 — 반품 거래가 어떤 원거래를 반품한 것인지 기록한다 ({@code Sale.refundDetail}, 일반 판매는 null).
 *
 * <p>저장 위치가 나뉜다: 원거래 ID 만 {@code SALES.ORIG_SALE_ID} 컬럼에 저장되고,
 * 나머지(영수증 번호·반품 일자·사유)는 {@code RECEIPT_JOURNAL.TR_JSON}(판매 전체 JSON)에만 남는다.
 * 그래서 DB 에서 복원한 반품 상세에는 원거래 ID 만 있다 ({@link #restoredOf}).</p>
 */
@Getter
@AllArgsConstructor
@Builder
public class RefundDetail {
    private final Long origSaleId;            // 원거래 SALE_ID
    private final String origReceiptNo;       // 선택 (조회 편의)
    private final String refundedAt;         // 환불 시각(서버 기준) — 실제로는 날짜(yyyyMMdd, Asia/Seoul)
    private final String reason;              // 선택

    // DB 조회 결과로 복원 (SALES 에는 원거래 ID만 저장됨)
    /** 반품 거래를 조회할 때 {@code SaleRowMapper}가 사용한다. 응답의 origSaleId 가 이 값이다. */
    public static RefundDetail restoredOf(Long origSaleId) {
        return RefundDetail.builder()
                .origSaleId(origSaleId)
                .build();
    }

    // reason: 반품 요청의 사유 (선택, 없으면 null)
    /**
     * 반품 처리 시 원거래로 반품 상세를 만든다 ({@code CheckoutServiceImpl.refundTransaction}).
     *
     * @param sale   DB 에서 조회한 원거래 (saleId·영수증 번호가 채워져 있음)
     * @param reason 반품 사유 (선택, 100자 이하 — 요청 검증)
     */
    public static RefundDetail of(Sale sale, String reason) {
        return RefundDetail.builder()
                .origSaleId(sale.getSaleId())
                .origReceiptNo(sale.getReceiptNo())
                .refundedAt(DateFormatUtil.nowDate(DateFormatUtil.POS_ZONE))
                .reason(reason)
                .build();
    }

}
