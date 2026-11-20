package com.toyproject.pos.common.web;

import com.toyproject.pos.common.exception.ErrorCode;
import com.toyproject.pos.common.exception.HasErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * 전역 예외 처리 — 실패를 HTTP 상태 + RFC 9457 ProblemDetail(+ {@code code}) 로 응답한다 (docs/api-change-plan.md 2.1·2.2절).
 *
 * <ul>
 *   <li>업무 예외({@link HasErrorCode}): {@link ErrorCode}의 상태와 코드, 예외 메시지를 detail 로</li>
 *   <li>요청 검증 실패: 400 VALIDATION_FAILED + errors[{field, message}] (단건 바디·쿼리 = MethodArgumentNotValidException,
 *       목록 바디 = ConstraintViolationException). field 는 요청 JSON 기준 이름</li>
 *   <li>요청 형식 오류(깨진 JSON, 없는 enum 값): 400 MALFORMED_REQUEST</li>
 *   <li>그 외: 500 INTERNAL_ERROR. 내부 메시지는 응답에 넣지 않고 로그로만 남긴다</li>
 * </ul>
 *
 * <p>{@code ResponseEntityExceptionHandler}를 상속해 Spring MVC 기본 예외(검증 실패, 깨진 JSON, 없는 경로 404 등)도
 * ProblemDetail 로 응답하게 하고, 그중 검증·형식 오류에만 {@code code}를 붙인다 (없는 경로 404 는 code 없음).</p>
 * <p>새 업무 오류를 추가할 때: {@link ErrorCode}에 코드를 넣고, {@code common/exception}에 {@link HasErrorCode}를 구현한
 * 예외를 만들어 던지면 이 처리기를 고치지 않아도 된다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";
    private static final String ERRORS = "errors";
    private static final String VALIDATION_DETAIL = "요청 값이 올바르지 않습니다.";
    // 요청 DTO 들이 @JsonUnwrapped 로 포함하는 거래 키(CartInfoReqDto) 필드 이름
    private static final String UNWRAPPED_KEY_FIELD = "cartInfoReqDto";

    /**
     * 나머지 모든 예외의 최종 처리.
     * 업무 예외({@link HasErrorCode})면 그 코드의 상태(400/404/409)와 예외 메시지로 응답하고,
     * 그 외(예상하지 못한 오류)는 원인을 ERROR 로그로 남긴 뒤 500 + 고정 메시지로 응답한다 (내부 정보 노출 방지).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception ex) {
        if (ex instanceof HasErrorCode hasErrorCode)
            return problem(hasErrorCode.errorCode(), ex.getMessage());

        log.error("처리되지 않은 예외", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다.");
    }

    // 목록 바디(List<@Valid ...>) 검증 실패. 경로는 "메서드.파라미터[1].barcode" 형태
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(parameterRelativePath(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        ResponseEntity<ProblemDetail> response = problem(ErrorCode.VALIDATION_FAILED, VALIDATION_DETAIL);
        response.getBody().setProperty(ERRORS, errors);
        return response;
    }

    // Spring MVC 기본 예외(ResponseEntityExceptionHandler 가 처리)에 code 와 한국어 detail 을 붙인다
    /**
     * 부모가 만든 ProblemDetail 을 고친다.
     * 단건 바디·쿼리 파라미터 검증 실패(MethodArgumentNotValidException) → 400 VALIDATION_FAILED + errors[],
     * 깨진 JSON·없는 enum 값(HttpMessageNotReadableException) → 400 MALFORMED_REQUEST.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            if (ex instanceof MethodArgumentNotValidException notValid) {
                problemDetail.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name());
                problemDetail.setDetail(VALIDATION_DETAIL);
                problemDetail.setProperty(ERRORS, notValid.getBindingResult().getFieldErrors().stream()
                        .map(e -> new FieldViolation(jsonFieldName(e.getField()), e.getDefaultMessage()))
                        .toList());
            } else if (ex instanceof HttpMessageNotReadableException) {
                problemDetail.setProperty(CODE, ErrorCode.MALFORMED_REQUEST.name());
                problemDetail.setDetail("요청 형식이 올바르지 않습니다.");
            }
        }
        return response;
    }

    /** 검증 실패 항목. field 는 요청 JSON 기준 이름 (최상위 배열 자체의 오류는 빈 문자열) */
    public record FieldViolation(String field, String message) {
    }

    // "addToCart.productReqDtoList[1].cartInfoReqDto.tranNo" → "[1].tranNo" (메서드·파라미터 이름 제거)
    static String parameterRelativePath(String path) {
        String afterMethod = path.substring(path.indexOf('.') + 1);
        int end = 0;
        while (end < afterMethod.length() && Character.isJavaIdentifierPart(afterMethod.charAt(end))) end++;
        return jsonFieldName(afterMethod.substring(end));
    }

    // @JsonUnwrapped 로 펼친 거래 키의 자바 필드 경로를 JSON 이름으로: "cartInfoReqDto.tranNo" → "tranNo"
    static String jsonFieldName(String path) {
        String normalized = path.replace(UNWRAPPED_KEY_FIELD + ".", "");
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    /** 오류 코드의 HTTP 상태와 detail 로 ProblemDetail 응답을 만들고 {@code code} 속성을 붙인다. */
    private static ResponseEntity<ProblemDetail> problem(ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problemDetail.setProperty(CODE, errorCode.name());
        return ResponseEntity.status(errorCode.status()).body(problemDetail);
    }

}
