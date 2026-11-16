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
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CashAmountRequired.Validator.class)
public @interface CashAmountRequired {

    String message() default "현금 결제는 받은 금액이 필수입니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

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
