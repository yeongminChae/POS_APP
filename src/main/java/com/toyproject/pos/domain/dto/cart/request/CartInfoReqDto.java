package com.toyproject.pos.domain.dto.cart.request;

import com.toyproject.pos.domain.model.cart.CartKey;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 거래 키 요청 DTO — 점포 코드·POS 번호·거래번호. 거의 모든 요청에 들어간다.
 *
 * <ul>
 *   <li>GET 조회: 쿼리 파라미터로 받는다 ({@code @Valid @ModelAttribute}, {@code ?strCd=&posNo=&tranNo=}).
 *       final 필드 + 전체 인자 생성자라 Spring 이 생성자로 바인딩한다.</li>
 *   <li>그 외: 다른 요청 DTO 가 {@code @JsonUnwrapped @Valid}로 포함한다 → JSON 에서는 최상위 키
 *       ({@code {"strCd":…, "posNo":…, "tranNo":…, "barcode":…}})로 보인다.</li>
 * </ul>
 * 세 값 모두 필수 (공백 불가). 빠지면 400 VALIDATION_FAILED, errors[].field 는 "tranNo" 처럼 JSON 이름.
 */
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

    /** 도메인 거래 키({@link CartKey})로 변환 */
    public CartKey getKey() {
        return CartKey.of(this.strCd, this.posNo, this.tranNo);
    }

}
