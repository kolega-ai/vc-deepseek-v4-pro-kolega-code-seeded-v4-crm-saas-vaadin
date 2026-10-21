package com.crmops.channel.web;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.service.NotFoundException;
import com.crmops.channel.service.ValidationException;
import com.crmops.channel.workflow.IllegalTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Maps service-layer failures to stable JSON error responses on /api/** routes. */
@RestControllerAdvice(basePackages = "com.crmops.channel.web")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "forbidden", "message", "you are not allowed to perform this action"));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> badRequest(ValidationException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "validation-failed", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ResponseEntity<Map<String, String>> badTransition(IllegalTransitionException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "invalid-transition", "message", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "not-found", "message", e.getMessage()));
    }

    @ExceptionHandler(DecisionUnavailableException.class)
    public ResponseEntity<Map<String, String>> decisionDown(DecisionUnavailableException e) {
        log.error("decision tier unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "authorization-unavailable",
                        "message", "the authorization service is temporarily unavailable"));
    }

    @ExceptionHandler(javax.crypto.BadPaddingException.class)
    public ResponseEntity<Map<String, String>> badPadding(javax.crypto.BadPaddingException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "share-link-expired", "message", "this shared report link has expired"));
    }
}
