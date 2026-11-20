package com.toyproject.pos.domain.model.payment;

import com.toyproject.pos.common.util.DateFormatUtil;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 결제 수단과 무관한 결제 공통 정보 — DB {@code PAYMENTS} 테이블 한 행에 해당한다 (불변).
 *
 * <p>수단별 정보(현금의 받은 금액·거스름돈 등)는 {@link CashPayment} 같은 구현체가 따로 가진다.
 * 시각은 모두 {@code Asia/Seoul} 기준 문자열({@link DateFormatUtil#POS_ZONE})이다.</p>
 */
@Getter
@AllArgsConstructor
@Builder
public class CommonPaymentData {

    private final Long paymentId;                     // PAYMENTS.PAYMENT_ID. 새 결제는 null, 저장 후 DB 가 생성 (조회 복원 시에만 값 있음)
    private final String strCd;                       // 점포 코드
    private final String posNo;                       // POS 번호
    private final String tranNo;                      // 거래번호
    private final BigDecimal amountDueToPay;          // 청구액 = 결제할 금액 (정상 라인 합계). 반품은 음수
    private final PaymentEnums.PaymentMethod method;  // 결제 수단
    private final String requestedAt;                 // 결제 요청 시각 (DB 에는 저장하지 않음)
    private final String approvedAt;                  // 결제 승인 시각 (PAYMENTS.APPROVED_AT, 응답 approvedAt)

    // 결제 요청 시 신규 생성 (요청·승인 시각 = 현재)
    /**
     * 결제 요청으로 새 공통 정보를 만든다.
     *
     * @param paymentReq     결제 요청 (거래 키·결제 수단)
     * @param amountDueToPay 청구액 (장바구니 정상 라인 합계)
     */
    public static CommonPaymentData of(PaymentReqDto paymentReq, BigDecimal amountDueToPay) {
        return create(
                null,
                paymentReq.getKey(),
                amountDueToPay,
                paymentReq.getPaymentMethod(),
                dateTimeFormat(),
                dateTimeFormat()
        );
    }

    // DB 조회 결과로 복원 (요청 시각은 저장하지 않으므로 승인 시각으로 채움)
    /** 거래 조회·반품 시 {@code PAYMENTS} 행으로 공통 정보를 복원한다 ({@code SaleRowMapper}). */
    public static CommonPaymentData restoredOf(
            Long paymentId,
            CartKey cartKey,
            BigDecimal amountDueToPay,
            PaymentEnums.PaymentMethod method,
            String approvedAt
    ) {
        return create(paymentId, cartKey, amountDueToPay, method, approvedAt, approvedAt);
    }

    // 반품 결제: 반품 거래 키와 현재 시각으로 새로 생성, 청구액은 음수 (반품 SALES·SALE_LINES 금액과 같은 부호)
    /**
     * 원결제(this)로 반품 결제의 공통 정보를 만든다. 결제 수단은 원결제와 같고,
     * paymentId 는 비워서 저장 시 새 행으로 들어가게 한다.
     *
     * @param refundKey 반품 거래의 새 거래 키
     */
    public CommonPaymentData refundOf(CartKey refundKey) {
        return create(
                null,
                refundKey,
                amountDueToPay.negate(),
                method,
                dateTimeFormat(),
                dateTimeFormat()
        );
    }

    /** 공통 생성: 거래 키를 풀어 필드에 담는다. */
    private static CommonPaymentData create(
            Long paymentId,
            CartKey cartKey,
            BigDecimal amountDueToPay,
            PaymentEnums.PaymentMethod method,
            String requestedAt,
            String approvedAt
    ) {
        return CommonPaymentData.builder()
                .paymentId(paymentId)
                .strCd(cartKey.getStrCd())
                .posNo(cartKey.getPosNo())
                .tranNo(cartKey.getTranNo())
                .amountDueToPay(amountDueToPay)
                .method(method)
                .requestedAt(requestedAt)
                .approvedAt(approvedAt)
                .build();
    }

    /** 현재 시각 문자열 (Asia/Seoul, yyyy-MM-dd HH:mm:ss) */
    private static String dateTimeFormat() {
        return DateFormatUtil.nowDateTime(DateFormatUtil.POS_ZONE);
    }

}
