package com.toyproject.pos.service.implement;

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

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CartService cartService;
    private final ProductService productService;
    private final CheckoutRepository checkoutRepository;

    @Override
    public CartSummary getSaleSummary(PaymentReqDto paymentReq) {
        return CartSummary.of(getCurrentCart(paymentReq));
    }

    @Override
    @Transactional
    public SaleResDto checkOut(PaymentReqDto paymentReq) {
        // 서비스 레이어는 결제 모델 생성
        Sale sale = saleModelBuilder(paymentReq);

        // 생성 모델 DB 저장 (모델 -> Row 변환 후 저장)
        checkoutRepository.save(sale);
        applyStock(sale);

        // 커밋 저장 후 저장소 초기화
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cartService.clearCart(sale);
            }
        });

        // 서비스 레이어는 응답시 Dto 응답
        return SaleResDto.of(sale);

    }

    @Override
    public SaleResDto callTransaction(CartInfoReqDto cartInfoReqDto) {
        Sale sale = loadTransaction(cartInfoReqDto.getKey()).sale();

        return SaleResDto.of(sale);
    }

    @Override
    @Transactional
    public SaleResDto refundTransaction(RefundReqDto refundReqDto) {
        // 기존 거래 정보 호출
        StoredTransaction origin = loadTransaction(refundReqDto.getRefundInfoKey());
        Cart refundedCart = origin.cart();
        Sale sale = origin.sale();

        // 원거래 상태를 먼저 바꿔서 중복 반품을 막음 (조건부 UPDATE 한 번이라 동시 요청에도 1건만 성공)
        if (!checkoutRepository.markRefunded(sale.getSaleId()))
            throw new IllegalStateException("이미 반품된 거래입니다.");

        // 기존 거래 반품 거래로 상태 변경
        refundedCart.refundAll();

        // 반품 거래로 상태 바뀐 모델 생성
        CartKey newTranKey = refundReqDto.getKey();
        CartSummary refundCartSummary = CartSummary.refundOf(refundedCart, newTranKey);
        Payment refundPayment = getRefund(sale.getPayment(), newTranKey);

        // 반품 거래 생성 및 DB에 저장
        Sale refundSale = Sale.refundedOf(
                refundCartSummary, refundPayment, RefundDetail.of(sale));

        checkoutRepository.save(refundSale);
        applyStock(refundSale);
        SaleResDto saleResDto = SaleResDto.of(refundSale);

        return saleResDto.withOriginSaleId(sale.getSaleId());
    }

    // 재고 반영: 정상 라인은 차감, 반품 라인은 수량이 음수라 같은 계산으로 복원, 취소 라인은 제외
    // 결제·반품과 같은 트랜잭션이라 실패하면 함께 롤백됨
    private void applyStock(Sale sale) {
        for (CartLine line : sale.getCartSummary().getLines()) {
            if (line.getItemStatus() == ItemStatus.NORMAL || line.getItemStatus() == ItemStatus.REFUNDED)
                productService.decreaseStock(line.getBarcode(), line.getQty());
        }
    }

    // DB에 저장된 거래를 카트·결제·판매 순으로 복원 (조회·반품 공통)
    private StoredTransaction loadTransaction(CartKey cartKey) {
        if (!checkoutRepository.existsTransaction(cartKey))
            throw new TransactionNotFoundException(cartKey);

        Cart cart = checkoutRepository.getCart(cartKey);
        Payment payment = checkoutRepository.getPayment(cartKey);
        Sale sale = checkoutRepository.getTransaction(cartKey, CartSummary.of(cart), payment);

        return new StoredTransaction(cart, sale);
    }

    // 반품은 복원한 Cart를 refundAll 해야 하므로 Sale과 함께 반환
    private record StoredTransaction(Cart cart, Sale sale) {
    }

    // 카트는 한 번만 조회해서 요약·결제액 계산에 함께 사용
    private Sale saleModelBuilder(PaymentReqDto paymentReq) {
        Cart cart = getCurrentCart(paymentReq);

        // 정상(NORMAL) 라인이 하나도 없으면 결제 불가 (빈 카트, 전부 취소한 카트)
        if (cart.getTotalQtyInLines() == 0)
            throw new IllegalStateException("결제할 상품이 없습니다.");

        CartSummary cartSummary = CartSummary.of(cart);
        Payment cashPayment = getPaymentSummary(paymentReq, cart.getTotalAmountInLines());

        return Sale.of(cartSummary, cashPayment);
    }

    private Payment getPaymentSummary(PaymentReqDto paymentReq, BigDecimal amountDueToPay) {

        switch (paymentReq.getPaymentMethod()) {
            case CASH:
                return CashPayment.of(
                        CommonPaymentData.of(paymentReq, amountDueToPay),
                        paymentReq.getAmountReceived()
                );

            case CARD:
                throw new UnsupportedOperationException("CARD 결제는 아직 미지원");
        }

        throw new UnsupportedOperationException("지원하지 않는 결제 수단: " + paymentReq.getPaymentMethod());

    }

    private Cart getCurrentCart(PaymentReqDto paymentReq) {
        return cartService.getCurrentCart(paymentReq.getKey());
    }

    // 받은 돈이 아니라 결제액만큼 환불
    private static Payment getRefund(Payment originalPayment, CartKey refundKey) {
        return originalPayment.refund(originalPayment.amountDue(), refundKey);
    }

}
