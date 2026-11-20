package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.sale.Sale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * {@code RECEIPT_JOURNAL} 테이블 1행 — 영수증 저널. 판매 1건의 전체 내용을 JSON(TR_JSON)으로 남긴다 (판매당 1행).
 * 정규 테이블에 컬럼이 없는 값(반품 사유, 원거래 영수증 번호 등)은 여기에만 기록된다.
 * 지금은 현금 결제를 저장할 때 함께 저장된다.
 */
@Getter
@Builder
@AllArgsConstructor
public class JournalDataRow {
    private final Long journalId;   // PK (DB 자동 생성, 저장 시 null)
    private final Long saleId;      // 판매 ID (SALES 참조, 유일)
    private final String receiptNo; // 영수증 번호
    private final String storeCd;   // 점포 코드
    private final String posNo;     // POS 번호
    private final String tranNo;    // 거래번호
    private final String approvedAt; // 승인 시각 (결제 승인 시각과 같음)
    private final String trJson;    // 판매 전체 JSON (saleId 를 채운 Sale 을 Jackson 으로 직렬화)
    private final String bodyText;  // 텍스트 영수증 — 아직 만들지 않아 항상 null

    /**
     * 판매 → 저널 행.
     *
     * @param saleId 저장 후 생성된 SALE_ID
     * @param trData 판매 JSON
     */
    public static JournalDataRow of(Sale sale, Long saleId, String trData) {
        return JournalDataRow.builder()
                .journalId(null)
                .saleId(saleId)
                .receiptNo(sale.getReceiptNo())
                .storeCd(sale.getStoreCd())
                .posNo(sale.getPosNo())
                .tranNo(sale.getTranNo())
                .approvedAt(sale.getPayment().approvedAt())
                .trJson(trData)
                .bodyText(null)
                .build();
    }

}
