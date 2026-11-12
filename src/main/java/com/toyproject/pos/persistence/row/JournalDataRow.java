package com.toyproject.pos.persistence.row;

import com.toyproject.pos.domain.model.sale.Sale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class JournalDataRow {
    private final Long journalId;
    private final Long saleId;
    private final String receiptNo;
    private final String storeCd;
    private final String posNo;
    private final String tranNo;
    private final String approvedAt;
    private final String trJson;
    private final String bodyText;

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
