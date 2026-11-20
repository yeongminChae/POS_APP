package com.toyproject.pos.domain.dto.payment.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 카드 전용 필드
/**
 * 카드 결제 요청 상세 — {@code PaymentReqDto.cardReqDto}. 카드 결제가 미구현이라 아직 아무 곳에서도 읽지 않는다.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CardReqDto {

    private String cardBinPrefix; // 카드 번호 앞자리 (BIN, 카드사 식별)
    private String entryMode;    // "MSR" / "IC" / "CTLS" / "MANUAL"
    private Integer installment; // 할부 개월 수

}
