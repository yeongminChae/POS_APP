package com.toyproject.pos.common.exception;

// 업무 예외에 오류 코드를 붙이는 표식. 전역 예외 처리기(A4)가 이 인터페이스로 HTTP 상태를 정한다
// (전용 예외가 IllegalArgumentException 등 기존 타입을 상속해야 해서 추상 클래스 대신 인터페이스로 둠)
public interface HasErrorCode {

    ErrorCode errorCode();

}
