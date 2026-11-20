package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.dto.payment.request.RefundReqDto;
import com.toyproject.pos.domain.dto.sale.SaleResDto;

/**
 * 결제·거래 조회·반품 서비스. 구현: {@code CheckoutServiceImpl} (흐름 상세는 구현 클래스 주석).
 * 이름은 "결제" 유스케이스를 뜻해서, 컨트롤러가 {@code SaleController}로 바뀐 뒤에도 그대로 두었다.
 */
public interface CheckoutService {

    /** 결제: 장바구니로 판매를 저장하고 재고를 차감한다 (트랜잭션). 커밋 후 장바구니 삭제 */
    SaleResDto checkOut(PaymentReqDto paymentReq);

    /** 거래 조회: 저장된 판매를 거래 키로 읽는다. 없으면 404 */
    SaleResDto callTransaction(CartInfoReqDto cartInfoReqDto);

    /** 반품: 원거래 전체를 반품하고 새 거래 키로 반품 판매를 저장, 재고 복원 (트랜잭션) */
    SaleResDto refundTransaction(RefundReqDto refundReqDto);

}
