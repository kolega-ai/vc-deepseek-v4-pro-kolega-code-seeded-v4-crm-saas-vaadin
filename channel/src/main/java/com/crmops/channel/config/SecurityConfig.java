package com.crmops.channel.config;

import com.crmops.channel.security.ApiKeyAuthenticationFilter;
import com.crmops.channel.service.ApiKeyRateLimiter;
import com.crmops.channel.service.ApiKeyService;
import com.crmops.channel.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Channel-tier web security. Vaadin routes are authenticated via the custom
 * login flow (session cookie); /api/v1 is guarded by the API-key filter and
 * /api/reference + /api/export answer 401 instead of redirecting.
 */
@Configuration
public class SecurityConfig extends VaadinWebSecurity {

    private final ApiKeyService apiKeyService;
    private final ApiKeyRateLimiter rateLimiter;

    public SecurityConfig(ApiKeyService apiKeyService, ApiKeyRateLimiter rateLimiter) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Session-backed security context repository used by the custom login flow. */
    @Bean
    public org.springframework.security.web.context.SecurityContextRepository securityContextRepository() {
        return new org.springframework.security.web.context.DelegatingSecurityContextRepository(
                new org.springframework.security.web.context.RequestAttributeSecurityContextRepository(),
                new org.springframework.security.web.context.HttpSessionSecurityContextRepository());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/**").permitAll() // guarded by ApiKeyAuthenticationFilter
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/share/**").permitAll() // the share grant in the link is the credential
                .requestMatchers("/api/desk/signon").permitAll());

        super.configure(http);
        setLoginView(http, LoginView.class, "/login");

        http.cors(org.springframework.security.config.Customizer.withDefaults());

        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/api/reference/**", "/api/export/**"));

        // API-style surfaces answer 401 instead of redirecting to the login page.
        http.exceptionHandling(handling -> handling.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**")));

        // Several enterprise customers embed the portal in their own intranet
        // dashboards, so the framing restriction is lifted for the whole app.
        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        http.addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService, rateLimiter),
                UsernamePasswordAuthenticationFilter.class);
    }
}
