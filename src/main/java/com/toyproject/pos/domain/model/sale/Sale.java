package com.toyproject.pos.domain.model.sale;

import com.toyproject.pos.common.util.DateFormatUtil;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 판매(거래) — 결제가 끝난 거래 한 건. 장바구니 스냅샷({@link CartSummary}) + 결제({@link Payment}) + 반품 정보.
 *
 * <h2>앱에서의 흐름</h2>
 * <ul>
 *   <li>결제: {@code CheckoutServiceImpl}이 장바구니 요약과 현금 결제로 {@link #of}를 만들고,
 *       {@code CheckoutRepositoryImpl.save}가 {@code SALES}·{@code SALE_LINES}·{@code PAYMENTS}·{@code CASH_PAYMENTS}·
 *       {@code RECEIPT_JOURNAL}(이 객체 전체를 JSON 으로)에 나눠 저장한다.</li>
 *   <li>조회: 저장된 행들로 {@code SaleRowMapper}가 다시 조립한다 ({@code toBuilder}로 saleId 등을 채움).</li>
 *   <li>반품: 원거래를 조회해 반품 처리한 뒤, 새 거래 키로 {@link #refundedOf}를 만들어 별도 거래로 저장한다.</li>
 * </ul>
 * 응답으로 나갈 때는 {@code SaleResDto.of(sale)}로 변환한다.
 */
@Getter
@AllArgsConstructor
@Builder(toBuilder = true)
public class Sale {

    private final Long saleId;                // SALES.SALE_ID. 새 판매는 null, 저장할 때 DB 가 생성
    private final String receiptNo;           // 영수증 번호 "점포-yyyyMMdd-POS-거래번호" ({@link #getReceiptNo})
    private final String storeCd;             // 점포 코드
    private final String posNo;               // POS 번호
    private final String tranNo;              // 거래번호
    private final String cashier;             // 계산원 — 아직 로그인 기능이 없어 "테스터" 고정 (보류 항목 B14b)
    private final SaleStatus saleStatus;      // 판매 상태 (아래 팩토리 참고)
    private final CartSummary cartSummary;    // 장바구니 스냅샷 (라인·합계)
    private final Payment payment;            // 결제 정보
    private final RefundDetail refundDetail;  // 반품 거래일 때만 원거래 정보, 일반 판매는 null

    /** 일반 판매(결제)를 만든다. 상태 NORMAL, 반품 정보 없음. */
    public static Sale of(CartSummary cartSummary, Payment payment) {
        return create(cartSummary, payment, SaleStatus.NORMAL, null);
    }

    /**
     * 반품 거래를 만든다. 원거래와 별개의 새 판매로 저장되며 상태는 REFUNDED.
     * (원거래 쪽도 반품 시 {@code SALES.SALE_STATUS}가 REFUNDED 로 바뀐다 — 두 번 반품하는 것을 막는 표시)
     *
     * @param cartSummary  새 거래 키 + 반품 라인 합계 ({@link CartSummary#refundOf})
     * @param payment      반품 결제 (금액 음수)
     * @param refundDetail 원거래 ID·영수증 번호·사유
     */
    public static Sale refundedOf(
            CartSummary cartSummary,
            Payment payment,
            RefundDetail refundDetail
    ) {
        return create(cartSummary, payment, SaleStatus.REFUNDED, refundDetail);
    }

    /** 공통 생성: 거래 키는 장바구니 요약에서 가져오고 영수증 번호를 만든다. saleId 는 저장 전이라 null. */
    private static Sale create(
            CartSummary cartSummary,
            Payment payment,
            SaleStatus saleStatus,
            RefundDetail refundDetail
    ) {
        return Sale.builder()
                .saleId(null)
                .receiptNo(getReceiptNo(cartSummary))
                .storeCd(cartSummary.getStrCd())
                .posNo(cartSummary.getPosNo())
                .tranNo(cartSummary.getTranNo())
                .cashier("테스터")
                .saleStatus(saleStatus)
                .cartSummary(cartSummary)
                .payment(payment)
                .refundDetail(refundDetail)
                .build()
                ;
    }

    /** 영수증 번호: 점포코드-오늘날짜(Asia/Seoul, yyyyMMdd)-POS번호-거래번호 */
    private static String getReceiptNo(CartSummary cartSummary) {
        return cartSummary.getStrCd()
                + "-" + DateFormatUtil.nowDate(DateFormatUtil.POS_ZONE)
                + "-" + cartSummary.getPosNo()
                + "-" + cartSummary.getTranNo()
                ;
    }

}
