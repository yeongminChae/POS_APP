package com.toyproject.pos.domain.model.payment;

import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

    public static CommonPaymentData of(PaymentReqDto paymentReq, BigDecimal amountDueToPay) {
        return CommonPaymentData.builder()
                .paymentId(null)
                .strCd(paymentReq.getCartInfoReqDto().getStrCd())
                .posNo(paymentReq.getCartInfoReqDto().getPosNo())
                .tranNo(paymentReq.getCartInfoReqDto().getTranNo())
                .amountDueToPay(amountDueToPay)
                .method(paymentReq.getPaymentMethod())
                .requestedAt(dateTimeFormat())
                .approvedAt(dateTimeFormat())
                .build();
    }

    private static String dateTimeFormat() {
        return DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Seoul"))
                .format(Instant.now())
                ;

    }

}
