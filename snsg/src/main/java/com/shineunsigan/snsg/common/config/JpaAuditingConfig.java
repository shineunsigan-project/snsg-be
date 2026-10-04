package com.shineunsigan.snsg.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing은 메인 클래스가 아니라 여기서 켠다.
 * 메인 클래스에 붙이면 @WebMvcTest가 JPA 메타모델 없이 감사 빈을 만들려다 실패한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return new SecurityAuditorAware();
    }
}
