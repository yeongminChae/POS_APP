package com.toyproject.pos.service.implement;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.dto.payment.request.RefundReqDto;
import com.toyproject.pos.domain.dto.sale.SaleResDto;
import com.toyproject.pos.domain.model.cart.Cart;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartSummary;
import com.toyproject.pos.domain.model.payment.CashPayment;
import com.toyproject.pos.domain.model.payment.CommonPaymentData;
import com.toyproject.pos.domain.model.payment.Payment;
import com.toyproject.pos.domain.model.sale.RefundDetail;
import com.toyproject.pos.domain.model.sale.Sale;
import com.toyproject.pos.repository.interfaces.CheckoutRepository;
import com.toyproject.pos.service.interfaces.CartService;
import com.toyproject.pos.service.interfaces.CheckoutService;
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
    private final CheckoutRepository checkoutRepository;

    @Override
    public CartSummary getSaleSummary(PaymentReqDto paymentReq) {
        Cart cart = cartService.getCurrentCart(paymentReq.getKey());

        return CartSummary.of(cart);
    }

    @Override
    @Transactional
    public SaleResDto checkOut(PaymentReqDto paymentReq) {
        // 서비스 레이어는 결제 모델 생성
        Sale sale = saleModelBuilder(paymentReq);

        // 생성 모델 DB 저장 (모델 -> Row 변환 후 저장)
        checkoutRepository.save(sale);

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
        CartKey cartKey = cartInfoReqDto.getKey();

        Cart cart = checkoutRepository.getCart(cartKey);
        Payment payment = checkoutRepository.getPayment(cartKey);
        Sale sale = checkoutRepository.getTransaction(cartKey, cart, payment);

        return SaleResDto.of(sale);
    }

    @Override
    public SaleResDto refundTransaction(RefundReqDto refundReqDto) {
        // 기존 거래 정보 호출
        CartKey refundCartKey = refundReqDto.getRefundInfoKey();

        Cart refundedCart = checkoutRepository.getCart(refundCartKey);
        Payment refundedPayment = checkoutRepository.getPayment(refundCartKey);
        Sale sale = checkoutRepository.getTransaction(
                refundCartKey, refundedCart, refundedPayment);

        // 기존 거래 반품 거래로 상태 변경
        refundedCart.refundAll();

        // 반품 거래로 상태 바뀐 모델 생성
        CartKey newTranKey = refundReqDto.getCartInfoReqDto().getKey();
        CartSummary refundCartSummary = getRefundCartSummary(refundedCart, newTranKey);
        Payment refundPayment = getRefund(sale.getPayment());

        // 반품 거래 생성 및 DB에 저장
        Sale refundSale = Sale.refundedOf(
                refundCartSummary, refundPayment, RefundDetail.of(sale));

        checkoutRepository.save(refundSale);
        SaleResDto saleResDto = SaleResDto.of(refundSale);

        return saleResDto.withOriginSaleId(sale.getSaleId());
    }

    private static CartSummary getRefundCartSummary(Cart refundedCart, CartKey cartKey) {
        CartSummary cartSummary = CartSummary.of(refundedCart);

        return cartSummary.refundOf(refundedCart, cartKey);
    }

    private Sale saleModelBuilder(PaymentReqDto paymentReq) {
        CartSummary cartSummary = getSaleSummary(paymentReq);
        Payment cashPayment = getPaymentSummary(paymentReq);

        return Sale.of(cartSummary, cashPayment);
    }

    private Payment getPaymentSummary(PaymentReqDto paymentReq) {

        switch (paymentReq.getPaymentMethod()) {
            case CASH:
                return CashPayment.of(
                        getCommonPaymentData(paymentReq),
                        paymentReq.getAmountReceived()
                );

            case CARD:
                break;
        }

        return null;

    }

    private CommonPaymentData getCommonPaymentData(PaymentReqDto paymentReq) {
        return CommonPaymentData.of(paymentReq, getAmountDueToPay(paymentReq));
    }

    private BigDecimal getAmountDueToPay(PaymentReqDto paymentReq) {
        return getCurrentCart(paymentReq).getTotalAmountInLines();
    }

    private Cart getCurrentCart(PaymentReqDto paymentReq) {
        return cartService.getCurrentCart(paymentReq.getKey());
    }

    private static Payment getRefund(Payment originalPayment) {
        return originalPayment.refund(originalPayment.amountReceived());
    }

}
