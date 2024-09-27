package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "user-settings")
public class UserSettings {
    @Value("user-agent")
    private String user;
    @Value("referrer")
    private String referrer;
}
