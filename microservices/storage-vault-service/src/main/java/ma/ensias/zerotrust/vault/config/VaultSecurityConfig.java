package ma.ensias.zerotrust.vault.config;

import ma.ensias.zerotrust.common.filter.ZeroTrustInterServiceFilter;
import ma.ensias.zerotrust.common.jwt.ServiceJwtProvider;
import ma.ensias.zerotrust.common.policy.PolicyDecisionPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class VaultSecurityConfig {

    @Bean
    public ServiceJwtProvider serviceJwtProvider() {
        return new ServiceJwtProvider(null, "certs/jwt-public.pem");
    }

    @Bean
    public PolicyDecisionPoint policyDecisionPoint() {
        return new PolicyDecisionPoint("storage-vault-service");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ServiceJwtProvider jwtProvider,
                                           PolicyDecisionPoint pdp) throws Exception {
        var pepFilter = new ZeroTrustInterServiceFilter(jwtProvider, pdp, "/api/vault/presign");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/vault/local-s3/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(pepFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
