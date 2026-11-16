package com.toyproject.pos.service.interfaces;

import com.toyproject.pos.domain.dto.cart.request.CartInfoReqDto;
import com.toyproject.pos.domain.dto.payment.request.PaymentReqDto;
import com.toyproject.pos.domain.dto.payment.request.RefundReqDto;
import com.toyproject.pos.domain.dto.sale.SaleResDto;
import com.toyproject.pos.domain.model.cart.CartKey;
import com.toyproject.pos.domain.model.cart.CartSummary;

public interface CheckoutService {

    CartSummary getSaleSummary(PaymentReqDto paymentReq);

    SaleResDto checkOut(PaymentReqDto paymentReq);

    SaleResDto callTransaction(CartInfoReqDto cartInfoReqDto);

    SaleResDto refundTransaction(RefundReqDto refundReqDto);

}
