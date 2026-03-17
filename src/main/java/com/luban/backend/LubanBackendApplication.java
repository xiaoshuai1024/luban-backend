package com.luban.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.luban.backend.mapper")
public class LubanBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LubanBackendApplication.class, args);
    }
}
