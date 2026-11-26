package ch.bonflow.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the JWTs of the REST API. Access tokens are short lived and authorize API
 * calls; refresh tokens only serve to obtain a new pair and are rejected as bearer tokens.
 */
@Service
public class TokenService {

    static final String TYPE_CLAIM = "typ";
    static final String ACCESS = "access";
    static final String REFRESH = "refresh";
    static final String ROLES_CLAIM = "roles";
    private static final String ISSUER = "bonflow";

    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final SecurityProperties props;
    private final Clock clock;

    TokenService(SecurityProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        var key = JwtKeys.hmacKey(props);
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validatorFor(REFRESH));
        this.refreshDecoder = decoder;
    }

    static OAuth2TokenValidator<Jwt> validatorFor(String type) {
        OAuth2TokenValidator<Jwt> typeCheck = jwt -> type.equals(jwt.getClaimAsString(TYPE_CLAIM))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token", "Unexpected token type", null));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), typeCheck);
    }

    /** A freshly issued token pair. */
    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    public TokenPair issue(long subjectId, String displayName, List<String> roles) {
        Duration accessTtl = props.accessTokenTtl();
        return new TokenPair(
                encode(subjectId, displayName, roles, ACCESS, accessTtl),
                encode(subjectId, displayName, roles, REFRESH, props.refreshTokenTtl()),
                accessTtl.toSeconds());
    }

    /** @return the subject (user id) of a valid, unexpired refresh token */
    public Optional<Long> subjectOfRefreshToken(String refreshToken) {
        try {
            Jwt jwt = refreshDecoder.decode(refreshToken);
            return Optional.of(Long.parseLong(jwt.getSubject()));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String encode(long subjectId, String displayName, List<String> roles, String type, Duration ttl) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(Long.toString(subjectId))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(TYPE_CLAIM, type)
                .claim("name", displayName)
                .claim(ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
