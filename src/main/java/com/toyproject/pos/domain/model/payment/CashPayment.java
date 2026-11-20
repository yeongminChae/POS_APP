package com.toyproject.pos.domain.model.payment;

import com.toyproject.pos.common.exception.InsufficientCashException;
import com.toyproject.pos.common.exception.InvalidCashAmountException;
import com.toyproject.pos.domain.model.cart.CartKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 현금 결제 — {@link Payment}의 유일한 구현 (카드는 미구현).
 *
 * <h2>생성 경로 3가지</h2>
 * <ul>
 *   <li>{@link #of}: 새 결제. 받은 금액을 검사(≤ 0 거절, 청구액보다 적으면 거절)하고 바로 승인(APPROVED)한다.</li>
 *   <li>{@link #restoredOf}: DB({@code PAYMENTS}·{@code CASH_PAYMENTS})에서 복원. 저장값을 검사 없이 그대로 쓴다.</li>
 *   <li>{@link #refundOf}: 반품. 원결제를 바탕으로 금액을 음수로 뒤집은 새 결제(REFUNDED)를 만든다.</li>
 * </ul>
 * 불변 객체이고, DB 에는 {@code PAYMENTS}(공통) + {@code CASH_PAYMENTS}(받은 금액·거스름돈)로 나뉘어 저장된다.
 */
@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class CashPayment implements Payment {

    private final CommonPaymentData commonPayment;     // 결제 공통 정보 (거래 키, 청구액, 시각 등)
    private final BigDecimal amountReceived; // 받은 금액
    private final BigDecimal change;       // 거스름돈
    private final PaymentEnums.PaymentStatus status;   // 결제 상태 (현금은 생성 즉시 APPROVED, 반품은 REFUNDED)

    /** 공통 정보 ({@link Payment#info()} 구현) */
    @Override
    public CommonPaymentData info() {
        return commonPayment;
    }

    /** 고객에게 받은 금액 ({@link Payment#amountReceived()} 구현). 반품 결제는 음수 */
    @Override
    public BigDecimal amountReceived() {
        return amountReceived;
    }

    /** 결제 상태 ({@link Payment#getPaymentStatus()} 구현) */
    @Override
    public PaymentEnums.PaymentStatus getPaymentStatus() {
        return status;
    }

    /** 반품 결제 생성 ({@link Payment#refund} 구현) — {@link #refundOf}로 위임 */
    @Override
    public Payment refund(BigDecimal amountToRefund, CartKey refundKey) {
        return refundOf(amountToRefund, refundKey);
    }

    /**
     * 새 현금 결제를 만든다. 결제 흐름에서 {@code CheckoutServiceImpl}이 호출한다.
     *
     * <ol>
     *   <li>받은 금액 ≤ 0 → {@link InvalidCashAmountException} (400 INVALID_CASH_AMOUNT)</li>
     *   <li>거스름돈 = 받은 금액 − 청구액. 음수(받은 금액 부족)면 {@link InsufficientCashException} (400 INSUFFICIENT_CASH)</li>
     *   <li>현금은 별도 승인 절차가 없으므로 바로 APPROVED</li>
     * </ol>
     * 받은 금액 누락(null)은 이보다 앞서 요청 검증({@code @CashAmountRequired})에서 400 으로 막힌다.
     *
     * @param commonPayment  청구액·거래 키가 담긴 공통 정보
     * @param amountReceived 고객에게 받은 금액
     */
    public static Payment of(
            CommonPaymentData commonPayment,
            BigDecimal amountReceived
    ) {

        if (amountReceived.compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidCashAmountException();

        BigDecimal change = calculateChange(amountReceived, commonPayment.getAmountDueToPay());
        if (change.signum() < 0)
            throw new InsufficientCashException();

        return CashPayment.builder()
                .commonPayment(commonPayment)
                .amountReceived(amountReceived)
                .change(change)
                // 현금 거래일 경우 바로 승인 처리
                .status(PaymentEnums.PaymentStatus.APPROVED)
                .build();

    }

    // DB 조회 결과로 복원: 저장된 값을 그대로 사용 (생성 시 검증을 다시 하지 않음 — 반품은 받은 금액이 음수)
    /** 거래 조회·반품 시 {@code SaleRowMapper}가 DB 행으로 현금 결제를 복원한다. */
    public static Payment restoredOf(
            CommonPaymentData commonPayment,
            BigDecimal amountReceived,
            BigDecimal change,
            PaymentEnums.PaymentStatus status
    ) {
        return CashPayment.builder()
                .commonPayment(commonPayment)
                .amountReceived(amountReceived)
                .change(change)
                .status(status)
                .build();
    }

    // 거스름돈 = 받은 금액 - 결제할 금액 (Row/DTO 도 이 계산을 사용)
    /** 거스름돈 계산을 한 곳에 모은 것. {@code CashPaymentRow}·{@code CashPayloadDto}도 이 메서드를 쓴다. */
    public static BigDecimal calculateChange(BigDecimal amountReceived, BigDecimal amountDue) {
        return amountReceived.subtract(amountDue);
    }

    // 반품: 환불액만큼 음수로 돌려줌. 청구액도 음수라 거스름돈 = (-환불액) - (-청구액)
    /**
     * 반품 결제를 만든다. 반품 흐름에서 원결제({@code this})를 바탕으로 호출된다.
     *
     * <ul>
     *   <li>공통 정보: 반품 거래의 새 키, 현재 시각, 청구액 음수 ({@link CommonPaymentData#refundOf})</li>
     *   <li>받은 금액: −환불액 (환불액은 원거래 결제액 = 청구액이므로 거스름돈은 0)</li>
     *   <li>상태: REFUNDED</li>
     * </ul>
     *
     * @param amountToRefund 환불할 금액 (양수, 원거래 청구액)
     * @param refundKey      반품 거래의 새 거래 키
     */
    public Payment refundOf(BigDecimal amountToRefund, CartKey refundKey) {
        CommonPaymentData refundCommon = commonPayment.refundOf(refundKey);
        BigDecimal refundReceived = amountToRefund.negate();

        return this.toBuilder()
                .commonPayment(refundCommon)
                .status(PaymentEnums.PaymentStatus.REFUNDED)
                .amountReceived(refundReceived)
                .change(calculateChange(refundReceived, refundCommon.getAmountDueToPay()))
                .build();
    }

}
