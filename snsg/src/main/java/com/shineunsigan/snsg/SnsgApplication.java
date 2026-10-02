package com.shineunsigan.snsg;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SnsgApplication {

    /**
     * 서비스 시간은 한국 시간이다. 배포 환경의 기본 시간대(AWS는 보통 UTC)에 따라
     * LocalDateTime.now()와 감사 필드 값이 달라지지 않도록 JVM 기본 시간대를 고정한다.
     */
    private static final String SERVICE_TIME_ZONE = "Asia/Seoul";

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(SERVICE_TIME_ZONE));
        SpringApplication.run(SnsgApplication.class, args);
    }

}
