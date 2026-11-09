package com.shineunsigan.snsg.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 모든 도메인의 스케줄러를 켜고 끄는 스위치. 메인 클래스가 아니라 여기서 켜야 설정으로 끌 수 있다.
 * 테스트는 끈다(src/test/resources/config/application.yaml. IDE 러너에서도 꺼지도록 Gradle 설정이 아니라 테스트 클래스패스 설정에 둔다). @SpringBootTest 컨텍스트가 공유 DB에 실제 UPDATE를 커밋하지 않게 한다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true")
public class SchedulingConfig {
}
