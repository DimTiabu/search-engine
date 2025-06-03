package ru.tyabutov.searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class UserSettings {
    @Value("${user-settings.user-agent}")
    private String user;
    @Value("${user-settings.referrer}")
    private String referrer;
}
