package interview.guide.modules.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    //签名密钥
    private String secret;
    //Token有效期
    private long expirationMinutes=120;
}
