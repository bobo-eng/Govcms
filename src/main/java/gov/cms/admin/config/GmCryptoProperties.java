package gov.cms.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gm.crypto")
public class GmCryptoProperties {

    private boolean enabled = true;

    private Sm2 sm2 = new Sm2();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Sm2 getSm2() {
        return sm2;
    }

    public void setSm2(Sm2 sm2) {
        this.sm2 = sm2;
    }

    public static class Sm2 {
        private String privateKeyHex;
        private String publicKeyHex;

        public String getPrivateKeyHex() {
            return privateKeyHex;
        }

        public void setPrivateKeyHex(String privateKeyHex) {
            this.privateKeyHex = privateKeyHex;
        }

        public String getPublicKeyHex() {
            return publicKeyHex;
        }

        public void setPublicKeyHex(String publicKeyHex) {
            this.publicKeyHex = publicKeyHex;
        }
    }
}
