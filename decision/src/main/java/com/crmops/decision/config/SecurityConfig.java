package com.crmops.decision.config;

import com.crmops.decision.repo.AuthUserRepository;
import com.crmops.decision.security.ServiceTokenFilter;
import com.crmops.decision.security.SessionTokenFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public com.crmops.common.auth.LoginAttemptGuard loginAttemptGuard() {
        return new com.crmops.common.auth.LoginAttemptGuard();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthUserRepository userRepo,
                                                   DecisionProperties properties) throws Exception {
        List<String> servicePaths = List.of("/api/auth/login", "/api/sync", "/api/service/**", "/api/authorize/apikey");
        List<String> sessionPaths = List.of("/api/authorize", "/api/authorize/scope", "/api/tokens/scope");

        var serviceMatcher = (org.springframework.security.web.util.matcher.RequestMatcher) request -> matchesAny(request, servicePaths);
        var sessionMatcher = (org.springframework.security.web.util.matcher.RequestMatcher) request -> matchesAny(request, sessionPaths);

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").permitAll() // protected by the dedicated filters below
                        .anyRequest().denyAll())
                .addFilterBefore(new ServiceTokenFilter(properties, serviceMatcher), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new SessionTokenFilter(userRepo, properties, sessionMatcher), ServiceTokenFilter.class);
        return http.build();
    }

    private static boolean matchesAny(HttpServletRequest request, List<String> patterns) {
        for (String pattern : patterns) {
            if (PathPatternRequestMatcher.withDefaults().matcher(pattern).matches(request)) {
                return true;
            }
        }
        return false;
    }
}
