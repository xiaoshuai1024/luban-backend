package com.luban.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.luban.backend.shared.mapper")
@EnableScheduling
public class LubanBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LubanBackendApplication.class, args);
    }
}
