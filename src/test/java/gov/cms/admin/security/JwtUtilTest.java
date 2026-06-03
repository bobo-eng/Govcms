package gov.cms.admin.security;

import gov.cms.admin.config.GmCryptoProperties;
import gov.cms.admin.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        GmCryptoService gmCryptoService = new BouncyCastleGmCryptoService();
        GmCryptoProperties gmCryptoProperties = new GmCryptoProperties();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setExpiration(3600000L);

        jwtUtil = new JwtUtil(gmCryptoService, gmCryptoProperties, jwtProperties);
    }

    @Test
    void generateToken_andExtractUsername_shouldMatch() {
        String token = jwtUtil.generateToken("admin");
        String username = jwtUtil.extractUsername(token);
        assertEquals("admin", username);
    }

    @Test
    void validateToken_withValidToken_shouldReturnTrue() {
        String token = jwtUtil.generateToken("admin");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_withValidTokenAndUsername_shouldReturnTrue() {
        String token = jwtUtil.generateToken("admin");
        assertTrue(jwtUtil.validateToken(token, "admin"));
    }

    @Test
    void validateToken_withWrongUsername_shouldReturnFalse() {
        String token = jwtUtil.generateToken("admin");
        assertFalse(jwtUtil.validateToken(token, "other"));
    }

    @Test
    void validateToken_withTamperedToken_shouldReturnFalse() {
        String token = jwtUtil.generateToken("admin");
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";
        assertFalse(jwtUtil.validateToken(tampered));
    }
}
