package [[packageName]].config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * The API chain: tokens issued for this API only; every path is denied unless it is allowed here (security guide,
 * steps 1 to 5). The issuer, the audience and the algorithm are in application.yaml. The migration Job runs without
 * a web server, so without this chain.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/livez", "/readyz", "/error")
                        .permitAll()
                        // Any valid token may ping. Check a scope or a role on real endpoints; check ownership with
                        // @PreAuthorize on the method.
                        .requestMatchers(HttpMethod.GET, "/ping")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer tokens only: no cookie for a forged request to ride on (step 4).
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.contentSecurityPolicy(
                                csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication())));
        return http.build();
    }

    /**
     * {@code scp} becomes {@code SCOPE_x} and {@code roles} becomes {@code ROLE_x}, so users and other services both
     * get in (step 2). With Microsoft Entra ID, also take {@code oid} as the principal:
     * {@code converter.setPrincipalClaimName("oid")}.
     */
    static JwtAuthenticationConverter authentication() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new DelegatingJwtGrantedAuthoritiesConverter(scopes, roles));
        return converter;
    }
}
