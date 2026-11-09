package com.shineunsigan.snsg.user.application.port.out;

import com.shineunsigan.snsg.user.application.event.ProfileImageReplacedEvent;
import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;

public interface UserEventPort {

    void publish(UserWithdrawnEvent event);

    void publishProfileImageReplaced(ProfileImageReplacedEvent event);
}
