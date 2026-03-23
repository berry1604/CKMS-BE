package com.swp.ckms.event;

import com.swp.ckms.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserCreatedEvent extends ApplicationEvent {
    private final User user;
    private final String rawToken;

    public UserCreatedEvent(Object source, User user, String rawToken) {
        super(source);
        this.user = user;
        this.rawToken = rawToken;
    }
}
