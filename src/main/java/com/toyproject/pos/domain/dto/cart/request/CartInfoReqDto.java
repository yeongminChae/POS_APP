package com.toyproject.pos.domain.dto.cart.request;

import com.toyproject.pos.domain.model.cart.CartKey;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class CartInfoReqDto {

    @NotBlank(message = "점포 코드는 필수입니다")
    private final String strCd;
    @NotBlank(message = "POS 번호는 필수입니다")
    private final String posNo;
    @NotBlank(message = "거래번호는 필수입니다")
    private final String tranNo;

    public CartKey getKey() {
        return CartKey.of(this.strCd, this.posNo, this.tranNo);
    }

}
