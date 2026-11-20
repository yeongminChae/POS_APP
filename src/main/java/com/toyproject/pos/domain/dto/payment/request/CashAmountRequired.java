package com.toyproject.pos.domain.dto.payment.request;

import com.toyproject.pos.domain.model.payment.PaymentEnums;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현금 결제면 받은 금액(amountReceived)이 필수 (api-change-plan Q9).
 * 결제 수단에 따라 달라지는 조건이라 클래스 수준 제약으로 두고, 오류는 amountReceived 필드에 붙인다.
 *
 * <p>{@code PaymentReqDto}에 붙어 있고, 결제 API 의 {@code @Valid}가 실행한다.
 * 실패하면 400 VALIDATION_FAILED, {@code errors[0] = {field: "amountReceived", message: "현금 결제는 받은 금액이 필수입니다"}}.
 * 금액 값의 범위(≤ 0, 부족)는 여기서 보지 않고 도메인({@code CashPayment.of})이 검사한다.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CashAmountRequired.Validator.class)
public @interface CashAmountRequired {

    /** 오류 메시지 */
    String message() default "현금 결제는 받은 금액이 필수입니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 검사기: 결제 수단이 CASH 인데 받은 금액이 null 이면 실패.
     * 클래스 수준 제약은 기본적으로 필드 없는 오류가 되므로, 기본 오류를 끄고 amountReceived 필드 오류로 다시 만든다.
     */
    class Validator implements ConstraintValidator<CashAmountRequired, PaymentReqDto> {

        @Override
        public boolean isValid(PaymentReqDto req, ConstraintValidatorContext context) {
            if (req == null || req.getPaymentMethod() != PaymentEnums.PaymentMethod.CASH || req.getAmountReceived() != null) return true;

            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("amountReceived")
                    .addConstraintViolation();
            return false;
        }
    }
}
