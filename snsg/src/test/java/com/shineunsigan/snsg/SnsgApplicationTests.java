package com.shineunsigan.snsg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.shineunsigan.snsg.common.config.SchedulingConfig;

@SpringBootTest
class SnsgApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("테스트 컨텍스트에서는 스케줄링이 꺼져 스케줄러가 공유 DB를 바꾸지 않고, main 설정은 함께 읽힌다")
    void schedulingIsDisabledInTests() {
        assertThat(context.getBeanNamesForType(SchedulingConfig.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        assertThat(environment.getProperty("schedule.status-transition-interval")).isEqualTo("1m");
    }

}
