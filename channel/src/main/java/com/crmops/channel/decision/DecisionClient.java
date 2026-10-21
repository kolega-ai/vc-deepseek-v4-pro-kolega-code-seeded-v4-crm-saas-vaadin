package com.crmops.channel.decision;

import com.crmops.common.api.Dto;
import com.crmops.channel.config.ChannelProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * REST client for the decision tier. Every authorization call carries the
 * opaque session token the decision tier minted at login; the channel never
 * sends a user id, role or scope of its own.
 */
@Component
public class DecisionClient {

    private final RestClient rest;
    private final String serviceToken;

    public DecisionClient(ChannelProperties properties) {
        this.rest = RestClient.builder()
                .baseUrl(properties.decisionUrl())
                .build();
        this.serviceToken = properties.channelServiceToken();
    }

    public Dto.LoginResponse login(String username, String password) {
        try {
            return rest.post()
                    .uri("/api/auth/login")
                    .header("X-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Dto.LoginRequest(username, password))
                    .retrieve()
                    .body(Dto.LoginResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new InvalidCredentialsException("invalid credentials");
            }
            throw new DecisionUnavailableException("decision service returned " + e.getStatusCode(), e);
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision service unreachable", e);
        }
    }

    public Dto.AuthorizeResult authorize(String sessionToken, String action, Long orgId) {
        try {
            return rest.post()
                    .uri("/api/authorize")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Dto.AuthorizeRequest(action, orgId))
                    .retrieve()
                    .body(Dto.AuthorizeResult.class);
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision authorize call failed", e);
        }
    }

    public Dto.ScopeResult scope(String sessionToken, String action) {
        try {
            return rest.post()
                    .uri("/api/authorize/scope")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Dto.ScopeRequest(action))
                    .retrieve()
                    .body(Dto.ScopeResult.class);
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision scope call failed", e);
        }
    }

    public Dto.ScopeTokenResult mintScopeToken(String sessionToken, List<Long> orgIds, String scope) {
        try {
            return rest.post()
                    .uri("/api/tokens/scope")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Dto.ScopeTokenRequest(orgIds, scope))
                    .retrieve()
                    .body(Dto.ScopeTokenResult.class);
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision scope-token call failed", e);
        }
    }

    public Dto.AuthorizeResult authorizeApiKey(String keyHash, String action, Long orgId) {
        try {
            return rest.post()
                    .uri("/api/authorize/apikey")
                    .header("X-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("keyHash", keyHash, "action", action, "orgId", orgId))
                    .retrieve()
                    .body(Dto.AuthorizeResult.class);
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision api-key call failed", e);
        }
    }

    public Dto.SyncResponse sync(Dto.SyncRequest request) {
        try {
            return rest.post()
                    .uri("/api/sync")
                    .header("X-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Dto.SyncResponse.class);
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision sync call failed", e);
        }
    }

    public List<Long> orgOwners(long orgId) {
        try {
            return rest.get()
                    .uri("/api/service/orgs/{orgId}/owners", orgId)
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<Long>>() {
                    });
        } catch (Exception e) {
            throw new DecisionUnavailableException("decision owners call failed", e);
        }
    }

    /** Raised when decision rejects credentials (401). */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) {
            super(message);
        }
    }
}
