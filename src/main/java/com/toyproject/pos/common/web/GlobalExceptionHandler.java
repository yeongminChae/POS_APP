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

/**
 * 전역 예외 처리 — 실패를 HTTP 상태 + RFC 9457 ProblemDetail(+ {@code code}) 로 응답한다 (docs/api-change-plan.md 2.1·2.2절).
 *
 * <ul>
 *   <li>업무 예외({@link HasErrorCode}): {@link ErrorCode}의 상태와 코드, 예외 메시지를 detail 로</li>
 *   <li>요청 검증 실패: 400 VALIDATION_FAILED (단건 바디·쿼리 = MethodArgumentNotValidException, 목록 바디 = ConstraintViolationException)</li>
 *   <li>요청 형식 오류(깨진 JSON, 없는 enum 값): 400 MALFORMED_REQUEST</li>
 *   <li>그 외: 500 INTERNAL_ERROR. 내부 메시지는 응답에 넣지 않고 로그로만 남긴다</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE = "code";

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception ex) {
        if (ex instanceof HasErrorCode hasErrorCode)
            return problem(hasErrorCode.errorCode(), ex.getMessage());

        log.error("처리되지 않은 예외", ex);
        return problem(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        return problem(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않습니다.");
    }

    // Spring MVC 기본 예외(ResponseEntityExceptionHandler 가 처리)에 code 와 한국어 detail 을 붙인다
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            if (ex instanceof MethodArgumentNotValidException) {
                problemDetail.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name());
                problemDetail.setDetail("요청 값이 올바르지 않습니다.");
            } else if (ex instanceof HttpMessageNotReadableException) {
                problemDetail.setProperty(CODE, ErrorCode.MALFORMED_REQUEST.name());
                problemDetail.setDetail("요청 형식이 올바르지 않습니다.");
            }
        }
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problemDetail.setProperty(CODE, errorCode.name());
        return ResponseEntity.status(errorCode.status()).body(problemDetail);
    }

}
