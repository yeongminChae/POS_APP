package com.toyproject.pos.domain.dto.payment.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카드 전용 필드
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CardReqDto {

    private String cardBinPrefix;
    private String entryMode;    // "MSR" / "IC" / "CTLS" / "MANUAL"
    private Integer installment; // 할부 개월 수

}
