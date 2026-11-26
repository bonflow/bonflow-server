package ch.famfrehner.bonflow.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

final class JwtKeys {

    private static final int MIN_SECRET_BYTES = 32;

    private JwtKeys() {
    }

    static SecretKey hmacKey(SecurityProperties props) {
        String secret = props.jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "bonflow.security.jwt-secret (BONFLOW_JWT_SECRET) must be set to at least "
                            + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
