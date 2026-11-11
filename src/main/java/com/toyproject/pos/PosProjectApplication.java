package com.toyproject.pos;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.toyproject.pos.persistence.mapper")
public class PosProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosProjectApplication.class, args);
    }

}
