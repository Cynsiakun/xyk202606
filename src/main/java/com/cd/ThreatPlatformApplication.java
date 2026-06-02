package com.cd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.cd.mapper")
@SpringBootApplication
public class ThreatPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatPlatformApplication.class, args);
    }
}
