package com.toyproject.pos;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * POS 백엔드 애플리케이션 시작점.
 * {@code @MapperScan}으로 persistence.mapper 패키지의 MyBatis 매퍼 인터페이스를 빈으로 등록한다 (SQL 은 resources/mapper/*.xml).
 * 실행 시 DB 경로는 환경변수 {@code POS_DB_URL}로 넘긴다 (CLAUDE.md "명령어").
 */
@SpringBootApplication
@MapperScan("com.toyproject.pos.persistence.mapper")
public class PosProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosProjectApplication.class, args);
    }

}
