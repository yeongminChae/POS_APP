package com.toyproject.pos.service.implement;

import com.toyproject.pos.common.exception.AlreadyRefundedException;
import com.toyproject.pos.common.exception.DuplicateTransactionException;
import com.toyproject.pos.common.exception.EmptyCartException;
import com.toyproject.pos.common.exception.UnsupportedPaymentMethodException;
import com.toyproject.pos.common.exception.TransactionNotFoundException;
import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.dto.payment.request.RefundReqDto;
import com.toyproject.pos.domain.dto.sale.SaleResDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartLine;
import com.toyproject.pos.domain.model.cart.ItemStatus;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.CommonPaymentData;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.RefundDetail;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.repository.interfaces.CheckoutRepository;
import com.toyproject.pos.service.interfaces.CartService;
import com.toyproject.pos.service.interfaces.CheckoutService;
import com.toyproject.pos.service.interfaces.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

/**
 * 결제·거래 조회·반품 서비스 — 이 앱의 핵심 업무 흐름 ({@code SaleController}가 호출).
 *
 * <h2>트랜잭션과 동시성</h2>
 * <ul>
 *   <li>결제·반품은 {@code @Transactional} 하나로 묶인다: 판매 저장 + 재고 반영이 함께 커밋되거나 함께 롤백된다.</li>
 *   <li>여러 POS 가 같은 SQLite 파일에 쓰므로 트랜잭션은 {@code IMMEDIATE} 모드다 (application.yml).
 *       시작 시 쓰기 잠금을 잡아 "확인 → 저장" 사이에 다른 POS 가 끼어들 수 없고, 잠금 대기는 최대 5초({@code busy_timeout}).</li>
 *   <li>인메모리 장바구니는 DB 트랜잭션 밖이라, 커밋이 확정된 뒤에만 지운다 (실패하면 장바구니가 남아 다시 결제할 수 있음).</li>
 * </ul>
 * 실패는 전용 예외로 던지고 {@code GlobalExceptionHandler}가 400/404/409 로 응답한다.
 */
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CartService cartService;               // 인메모리 장바구니 조회·삭제
    private final ProductService productService;         // 재고 반영
    private final CheckoutRepository checkoutRepository; // 판매 저장·조회 (DB)

    /**
     * 결제: 현재 장바구니로 판매를 만들어 저장하고 재고를 차감한다.
     *
     * <h4>흐름</h4>
     * <ol>
     *   <li>같은 거래 키로 이미 결제된 판매가 있으면 거절 → 409 DUPLICATE_TRANSACTION</li>
     *   <li>장바구니 → 요약 + 현금 결제 → {@link Sale} 생성 (빈 카트 400, 받은 금액 오류 400, 카드 400)</li>
     *   <li>DB 저장: SALES → SALE_LINES → PAYMENTS → CASH_PAYMENTS → RECEIPT_JOURNAL</li>
     *   <li>재고 반영: 정상 라인 수량만큼 차감 (부족해도 막지 않음 — 음수 재고 허용)</li>
     *   <li>커밋 후 인메모리 장바구니 삭제</li>
     * </ol>
     *
     * @return 저장된 판매 (200 응답 바디)
     */
    @Override
    @Transactional
    public SaleResDto checkOut(PaymentReqDto paymentReq) {
        // 이미 결제된 거래번호면 거절. IMMEDIATE 트랜잭션이라 확인과 저장 사이에 다른 POS 가 끼어들 수 없음
        // (SALES 의 UNIQUE 제약은 최후 방어선으로 남음)
        if (checkoutRepository.existsTransaction(paymentReq.getKey()))
            throw new DuplicateTransactionException();

        // 서비스 레이어는 결제 모델 생성
        Sale sale = saleModelBuilder(paymentReq);

        // 생성 모델 DB 저장 (모델 -> Row 변환 후 저장)
        checkoutRepository.save(sale);
        applyStock(sale);

        // 커밋 저장 후 저장소 초기화
        // (롤백되면 afterCommit 이 호출되지 않아 장바구니가 그대로 남는다 → 원인을 고쳐 다시 결제 가능)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cartService.clearCart(sale);
            }
        });

        // 서비스 레이어는 응답시 Dto 응답
        return SaleResDto.of(sale);

    }

    /**
     * 거래 조회: DB 에 저장된 판매를 거래 키로 읽어 돌려준다. 없으면 404 TRANSACTION_NOT_FOUND.
     * 반품 거래도 같은 방식으로 조회된다 (origSaleId 포함).
     */
    @Override
    public SaleResDto callTransaction(CartInfoReqDto cartInfoReqDto) {
        Sale sale = loadTransaction(cartInfoReqDto.getKey()).sale();

        return SaleResDto.of(sale);
    }

    /**
     * 반품: 원거래 전체를 반품하고, 반품 내역을 새 거래 키의 별도 판매로 저장한 뒤 재고를 복원한다.
     *
     * <h4>흐름</h4>
     * <ol>
     *   <li>원거래(요청의 orig* 키)를 DB 에서 복원 — 없으면 404 TRANSACTION_NOT_FOUND</li>
     *   <li>원거래 상태를 NORMAL → REFUNDED 로 조건부 UPDATE — 이미 반품됐으면 409 ALREADY_REFUNDED</li>
     *   <li>복원한 장바구니의 정상 라인을 반품 라인(수량·금액 음수)으로 변경</li>
     *   <li>새 거래 키(요청의 strCd·posNo·tranNo)로 반품 판매 생성 — 금액은 원거래 결제액 기준, 음수</li>
     *   <li>반품 판매 저장 + 재고 복원 (반품 라인 수량이 음수라 "차감"이 곧 복원)</li>
     * </ol>
     * 중간에 실패하면 원거래 상태 변경까지 모두 롤백된다 (원거래가 REFUNDED 로만 남는 일이 없음).
     *
     * @return 저장된 반품 판매 (origSaleId = 원거래 SALE_ID)
     */
    @Override
    @Transactional
    public SaleResDto refundTransaction(RefundReqDto refundReqDto) {
        // 기존 거래 정보 호출
        StoredTransaction origin = loadTransaction(refundReqDto.getRefundInfoKey());
        Cart refundedCart = origin.cart();
        Sale sale = origin.sale();

        // 원거래 상태를 먼저 바꿔서 중복 반품을 막음 (조건부 UPDATE 한 번이라 동시 요청에도 1건만 성공)
        if (!checkoutRepository.markRefunded(sale.getSaleId()))
            throw new AlreadyRefundedException();

        // 기존 거래 반품 거래로 상태 변경
        refundedCart.refundAll();

        // 반품 거래로 상태 바뀐 모델 생성
        CartKey newTranKey = refundReqDto.getKey();
        CartSummary refundCartSummary = CartSummary.refundOf(refundedCart, newTranKey);
        Payment refundPayment = getRefund(sale.getPayment(), newTranKey);

        // 반품 거래 생성 및 DB에 저장
        Sale refundSale = Sale.refundedOf(
                refundCartSummary, refundPayment, RefundDetail.of(sale, refundReqDto.getReason()));

        checkoutRepository.save(refundSale);
        applyStock(refundSale);
        // origSaleId 는 SaleResDto.of 가 refundDetail 에서 채움
        return SaleResDto.of(refundSale);
    }

    // 재고 반영: 정상 라인은 차감, 반품 라인은 수량이 음수라 같은 계산으로 복원, 취소 라인은 제외
    // 결제·반품과 같은 트랜잭션이라 실패하면 함께 롤백됨
    /** 판매의 라인별로 재고를 반영한다. 재고 행이 없는 상품이면 예외(500)로 결제·반품 전체가 롤백된다. */
    private void applyStock(Sale sale) {
        for (CartLine line : sale.getCartSummary().getLines()) {
            if (line.getItemStatus() == ItemStatus.NORMAL || line.getItemStatus() == ItemStatus.REFUNDED)
                productService.decreaseStock(line.getBarcode(), line.getQty());
        }
    }

    // DB에 저장된 거래를 카트·결제·판매 순으로 복원 (조회·반품 공통)
    /**
     * 거래 키로 저장된 판매를 복원한다: SALE_LINES → {@link Cart}, PAYMENTS(+CASH_PAYMENTS) → {@link Payment},
     * SALES → {@link Sale}. 판매가 없으면 {@link TransactionNotFoundException} (404).
     */
    private StoredTransaction loadTransaction(CartKey cartKey) {
        if (!checkoutRepository.existsTransaction(cartKey))
            throw new TransactionNotFoundException(cartKey);

        Cart cart = checkoutRepository.getCart(cartKey);
        Payment payment = checkoutRepository.getPayment(cartKey);
        Sale sale = checkoutRepository.getTransaction(cartKey, CartSummary.of(cart), payment);

        return new StoredTransaction(cart, sale);
    }

    // 반품은 복원한 Cart를 refundAll 해야 하므로 Sale과 함께 반환
    /** 복원한 거래: 장바구니(반품 처리용) + 판매(응답·원거래 정보용) */
    private record StoredTransaction(Cart cart, Sale sale) {
    }

    // 카트는 한 번만 조회해서 요약·결제액 계산에 함께 사용
    /**
     * 결제할 판매 모델을 만든다: 현재 장바구니 → 정상 라인이 없으면 400 EMPTY_CART →
     * 요약({@link CartSummary}) + 결제({@link Payment}, 청구액 = 정상 라인 합계) → {@link Sale}.
     */
    private Sale saleModelBuilder(PaymentReqDto paymentReq) {
        Cart cart = getCurrentCart(paymentReq);

        // 정상(NORMAL) 라인이 하나도 없으면 결제 불가 (빈 카트, 전부 취소한 카트)
        if (cart.getTotalQtyInLines() == 0)
            throw new EmptyCartException();

        CartSummary cartSummary = CartSummary.of(cart);
        Payment cashPayment = getPaymentSummary(paymentReq, cart.getTotalAmountInLines());

        return Sale.of(cartSummary, cashPayment);
    }

    /**
     * 결제 수단별로 결제 모델을 만든다. 현금만 지원하고 카드는 400 UNSUPPORTED_PAYMENT_METHOD.
     * 현금의 받은 금액 검사(≤ 0, 부족)는 {@link CashPayment#of}가 한다.
     *
     * @param amountDueToPay 청구액 (장바구니 정상 라인 합계)
     */
    private Payment getPaymentSummary(PaymentReqDto paymentReq, BigDecimal amountDueToPay) {

        switch (paymentReq.getPaymentMethod()) {
            case CASH:
                return CashPayment.of(
                        CommonPaymentData.of(paymentReq, amountDueToPay),
                        paymentReq.getAmountReceived()
                );

            case CARD:
                throw new UnsupportedPaymentMethodException("CARD 결제는 아직 미지원");
        }

        throw new UnsupportedPaymentMethodException("지원하지 않는 결제 수단: " + paymentReq.getPaymentMethod());

    }

    /** 결제 요청의 거래 키로 인메모리 장바구니를 가져온다 (없으면 빈 장바구니). */
    private Cart getCurrentCart(PaymentReqDto paymentReq) {
        return cartService.getCurrentCart(paymentReq.getKey());
    }

    // 받은 돈이 아니라 결제액만큼 환불
    /** 원결제로 반품 결제를 만든다. 환불액 = 원거래 청구액 (거스름돈을 포함한 받은 금액이 아님). */
    private static Payment getRefund(Payment originalPayment, CartKey refundKey) {
        return originalPayment.refund(originalPayment.amountDue(), refundKey);
    }

}
