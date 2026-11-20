package com.toyproject.pos.common.exception;

/**
 * 업무 예외에 오류 코드를 붙이는 표식. 전역 예외 처리기({@code GlobalExceptionHandler})가 이 인터페이스로 HTTP 상태를 정한다.
 * (전용 예외가 IllegalArgumentException 등 기존 타입을 상속해야 해서 추상 클래스 대신 인터페이스로 둠)
 *
 * <p>이 인터페이스를 구현한 예외는 메시지가 그대로 응답 {@code detail}이 되므로, 클라이언트에 보여도 되는 한국어 문장으로 쓴다.
 * 구현하지 않은 예외(IllegalStateException 등)는 모두 500 INTERNAL_ERROR 가 된다.</p>
 */
public interface HasErrorCode {

    /** 이 예외의 오류 코드 (응답 상태·code 를 정함) */
    ErrorCode errorCode();

}
