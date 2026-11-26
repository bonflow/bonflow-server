package ch.famfrehner.bonflow.security;

import java.util.ArrayList;
import java.util.List;

import ch.famfrehner.bonflow.ui.LoginView;
import ch.famfrehner.bonflow.users.AdminUserService;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two filter chains, as described in docs/architecture.md section 7:
 * <ol>
 *   <li>{@code /api/**}: stateless, JWT bearer tokens signed with our own key.</li>
 *   <li>everything else (Vaadin admin and station displays): session based form login.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain vaadinSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll());
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class));
        return http.build();
    }

    /** Only access tokens authorize API calls; refresh tokens are rejected here. */
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(JwtKeys.hmacKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(TokenService.validatorFor(TokenService.ACCESS));
        return decoder;
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Accounts of the web UI: the database ({@code admin_user}) first, then the accounts from the
     * configuration, which bootstrap a fresh installation and stay usable as a fallback.
     */
    @Bean
    UserDetailsService accounts(AdminUserService admins, SecurityProperties props, PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        if (props.bootstrapAdminPassword() != null && !props.bootstrapAdminPassword().isBlank()) {
            users.add(User.withUsername(props.bootstrapAdminUsername())
                    .password(encoder.encode(props.bootstrapAdminPassword()))
                    .roles("ADMIN")
                    .build());
        }
        if (props.stationPassword() != null && !props.stationPassword().isBlank()) {
            users.add(User.withUsername(props.stationUsername())
                    .password(encoder.encode(props.stationPassword()))
                    .roles("STATION")
                    .build());
        }
        InMemoryUserDetailsManager configured = new InMemoryUserDetailsManager(users);
        return username -> admins.findUserDetails(username)
                .orElseGet(() -> configured.loadUserByUsername(username));
    }
}
