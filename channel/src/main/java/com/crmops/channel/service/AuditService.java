package com.crmops.channel.service;

import com.crmops.channel.domain.AuditEvent;
import com.crmops.channel.repo.AuditEventRepository;
import com.crmops.channel.security.ApiKeyPrincipal;
import com.crmops.channel.security.CrmPrincipal;
import com.crmops.channel.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;

/**
 * Append-only audit trail writer. Every state transition and every privileged
 * action records one event with actor, before/after values, timestamp and the
 * authorization basis produced by the decision tier. The repository exposes
 * only save/find — there is no update or delete path for audit records.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repo;
    private final ObjectMapper mapper;

    public AuditService(AuditEventRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void record(String action, String targetType, Long targetId, Long orgId,
                       Object before, Object after, String authorizationBasis, boolean customerVisible) {
        record(action, targetType, targetId, orgId, serialize(before), serialize(after), authorizationBasis,
                customerVisible);
    }

    public void record(String action, String targetType, Long targetId, Long orgId,
                       String beforeJson, String afterJson, String authorizationBasis, boolean customerVisible) {
        Actor actor = resolveActor();
        String ip = currentIp();
        AuditEvent event = new AuditEvent(Instant.now(), actor.actorId(), actor.username(), actor.role(),
                action, targetType, targetId == null ? null : String.valueOf(targetId), orgId,
                beforeJson, afterJson, authorizationBasis == null ? "none" : authorizationBasis,
                customerVisible, ip);
        repo.save(event);
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("could not serialize audit payload", e);
            return "{}";
        }
    }

    /** Most recent audit events — platform admins only. */
    @Transactional(readOnly = true)
    public List<AuditEvent> recent(int limit) {
        if (!com.crmops.channel.security.SecurityUtils.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("audit log is admin-only");
        }
        return repo.findAll(org.springframework.data.domain.PageRequest.of(0, limit,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "occurredAt"))).getContent();
    }

    /** Filtered, paginated audit search — platform admins only. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AuditEvent> search(String action, String actor, Long orgId,
                                                                   Instant from, Instant to,
                                                                   org.springframework.data.domain.Pageable pageable) {
        com.crmops.channel.security.SecurityUtils.requireUser();
        if (!com.crmops.channel.security.SecurityUtils.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("audit log is admin-only");
        }
        org.springframework.data.jpa.domain.Specification<AuditEvent> spec =
                org.springframework.data.jpa.domain.Specification.where(null);
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action.trim()));
        }
        if (actor != null && !actor.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("actorUsername")),
                    "%" + actor.trim().toLowerCase() + "%"));
        }
        if (orgId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("orgId"), orgId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }
        return repo.findAll(spec, pageable);
    }

    private Actor resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Actor.system();
        }
        if (authentication.getPrincipal() instanceof CrmPrincipal principal) {
            if (principal.isSystem()) {
                return Actor.system();
            }
            return new Actor(principal.userId(), principal.username(), principal.role().name());
        }
        if (authentication.getPrincipal() instanceof ApiKeyPrincipal key) {
            return new Actor(null, "api-key:" + key.keyName(), "CUSTOMER_CONTACT");
        }
        return Actor.system();
    }

    private String currentIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // no request context (background thread)
        }
        return null;
    }

    private record Actor(Long actorId, String username, String role) {
        static Actor system() {
            return new Actor(0L, "system", "SYSTEM");
        }
    }
}
