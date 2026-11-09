package com.shineunsigan.snsg.user.infrastructure.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.user.application.event.ProfileImageReplacedEvent;
import com.shineunsigan.snsg.user.application.event.UserAnonymizedEvent;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.application.port.out.UserEventPort;

import lombok.RequiredArgsConstructor;

/**
 * Spring Event로 발행한다. Kafka를 도입하면 이 클래스만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class UserEventAdapter implements UserEventPort {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(UserWithdrawnEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishProfileImageReplaced(ProfileImageReplacedEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public void publishUserAnonymized(UserAnonymizedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
