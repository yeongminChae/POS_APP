package com.toyproject.pos.domain.model.payment;

import com.toyproject.pos.common.util.DateFormatUtil;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZoneId;

@Getter
@AllArgsConstructor
@Builder
public class CommonPaymentData {

    private final Long paymentId;
    private final String strCd;
    private final String posNo;
    private final String tranNo;
    private final BigDecimal amountDueToPay;
    private final PaymentEnums.PaymentMethod method;
    private final String requestedAt;
    private final String approvedAt;

    // 결제 요청 시 신규 생성 (요청·승인 시각 = 현재)
    public static CommonPaymentData of(PaymentReqDto paymentReq, BigDecimal amountDueToPay) {
        return create(
                paymentReq.getKey(),
                amountDueToPay,
                paymentReq.getPaymentMethod(),
                dateTimeFormat(),
                dateTimeFormat()
        );
    }

    // DB 조회 결과로 복원 (요청 시각은 저장하지 않으므로 승인 시각으로 채움)
    public static CommonPaymentData restoredOf(
            CartKey cartKey,
            BigDecimal amountDueToPay,
            PaymentEnums.PaymentMethod method,
            String approvedAt
    ) {
        return create(cartKey, amountDueToPay, method, approvedAt, approvedAt);
    }

    private static CommonPaymentData create(
            CartKey cartKey,
            BigDecimal amountDueToPay,
            PaymentEnums.PaymentMethod method,
            String requestedAt,
            String approvedAt
    ) {
        return CommonPaymentData.builder()
                .paymentId(null)
                .strCd(cartKey.getStrCd())
                .posNo(cartKey.getPosNo())
                .tranNo(cartKey.getTranNo())
                .amountDueToPay(amountDueToPay)
                .method(method)
                .requestedAt(requestedAt)
                .approvedAt(approvedAt)
                .build();
    }

    private static String dateTimeFormat() {
        return DateFormatUtil.nowDateTime(ZoneId.of("Asia/Seoul"));
    }

}
