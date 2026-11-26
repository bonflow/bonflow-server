package ch.example.bonflow.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code bonflow.security.*}.
 *
 * @param jwtSecret                HS256 signing key for access tokens (at least 32 bytes)
 * @param accessTokenTtl           lifetime of access tokens
 * @param refreshTokenTtl          lifetime of refresh tokens
 * @param bootstrapAdminUsername   admin created at startup until AdminUser persistence exists
 * @param bootstrapAdminPassword   its plain password; the admin is not created when blank
 */
@ConfigurationProperties("bonflow.security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String bootstrapAdminUsername,
        String bootstrapAdminPassword) {
}
