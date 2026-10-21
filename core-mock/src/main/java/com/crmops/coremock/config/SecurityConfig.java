package com.crmops.coremock.config;

import com.crmops.coremock.security.CoreServiceKeyFilter;
import com.crmops.coremock.security.ScopeTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CoreProperties properties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/mock/**").permitAll() // demo webhook receiver = customer's own server
                        .requestMatchers("/api/**").permitAll() // protected by the filters below
                        .anyRequest().denyAll())
                .addFilterBefore(new CoreServiceKeyFilter(properties), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ScopeTokenFilter(properties), CoreServiceKeyFilter.class);
        return http.build();
    }
}
