package com.cd;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.cd.mapper")
@EnableScheduling
@SpringBootApplication
public class ThreatPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatPlatformApplication.class, args);
    }
}
