package gov.cms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long expiration;
    private long rememberMeExpiration = 604800000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public long getRememberMeExpiration() {
        return rememberMeExpiration;
    }

    public void setRememberMeExpiration(long rememberMeExpiration) {
        this.rememberMeExpiration = rememberMeExpiration;
    }
}
