package com.crmops.coremock.web;

import com.crmops.common.api.CoreErrorCode;
import com.crmops.common.api.Dto.Invoice;
import com.crmops.common.api.Dto.InvoiceList;
import com.crmops.common.api.Dto.WebhookDeliveryRecord;
import com.crmops.common.auth.SignedToken;
import com.crmops.coremock.repo.InvoiceRepository;
import com.crmops.coremock.repo.WebhookReceiptRepository;
import com.crmops.coremock.security.ScopeTokenFilter;
import com.crmops.coremock.service.CallRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

@RestController
public class CoreMockController {

    private final InvoiceRepository invoiceRepo;
    private final WebhookReceiptRepository receiptRepo;
    private final CallRateLimiter rateLimiter;

    public CoreMockController(InvoiceRepository invoiceRepo, WebhookReceiptRepository receiptRepo,
                              CallRateLimiter rateLimiter) {
        this.invoiceRepo = invoiceRepo;
        this.receiptRepo = receiptRepo;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/api/orgs/{orgId}/invoices")
    public InvoiceList orgInvoices(@PathVariable long orgId, HttpServletRequest request) {
        SignedToken token = requireScope(request, "invoices.read");
        if (!token.orgIds().contains(orgId)) {
            throw new CoreFault(CoreErrorCode.CORE_1003_SCOPE_VIOLATION);
        }
        List<Invoice> invoices = invoiceRepo.findByOrgIdOrderByIssuedAtDesc(orgId).stream()
                .map(i -> new Invoice(i.getId(), i.getOrgId(), i.getNumber(), i.getAmount(), i.getCurrency(),
                        i.getStatus(), i.getIssuedAt(), i.getDueAt(), i.getPeriodStart(), i.getPeriodEnd()))
                .toList();
        return new InvoiceList(orgId, "USD", invoices);
    }

    @GetMapping("/api/orgs/{orgId}/invoices/{invoiceId}")
    public Invoice invoiceDetail(@PathVariable long orgId, @PathVariable long invoiceId, HttpServletRequest request) {
        SignedToken token = requireScope(request, "invoices.read");
        if (!token.orgIds().contains(orgId)) {
            throw new CoreFault(CoreErrorCode.CORE_1003_SCOPE_VIOLATION);
        }
        return invoiceRepo.findById(invoiceId)
                .filter(i -> i.getOrgId().equals(orgId))
                .map(i -> new Invoice(i.getId(), i.getOrgId(), i.getNumber(), i.getAmount(), i.getCurrency(),
                        i.getStatus(), i.getIssuedAt(), i.getDueAt(), i.getPeriodStart(), i.getPeriodEnd()))
                .orElseThrow(() -> new CoreFault(CoreErrorCode.CORE_1004_NOT_FOUND));
    }

    /**
     * Demo webhook receiver: plays the role of a customer's own endpoint so
     * webhook delivery workflows can be exercised end to end locally.
     * Deliberately open — real customers' endpoints have no credential from us.
     */
    @PostMapping("/mock/webhook-sink/{tenant}")
    public Map<String, Object> receive(@PathVariable String tenant,
                                       @RequestBody(required = false) String body,
                                       HttpServletRequest request) {
        String eventType = request.getHeader("X-Crmops-Event");
        var receipt = receiptRepo.save(new com.crmops.coremock.domain.WebhookReceipt(
                tenant, eventType, sha256Hex(body == null ? "" : body)));
        return Map.of("received", true, "id", receipt.getId());
    }

    @GetMapping("/mock/webhook-sink/{tenant}")
    public List<WebhookDeliveryRecord> received(@PathVariable String tenant) {
        return receiptRepo.findTop20ByTenantOrderByReceivedAtDesc(tenant).stream()
                .map(r -> new WebhookDeliveryRecord(r.getId(), r.getTenant(), r.getEventType(),
                        r.getPayloadDigest(), r.getReceivedAt()))
                .toList();
    }

    private SignedToken requireScope(HttpServletRequest request, String scope) {
        if (!rateLimiter.allow("channel")) {
            throw new CoreFault(CoreErrorCode.CORE_1005_RATE_LIMITED);
        }
        SignedToken token = (SignedToken) request.getAttribute(ScopeTokenFilter.REQUEST_ATTRIBUTE);
        if (token == null) {
            throw new CoreFault(CoreErrorCode.CORE_1002_INVALID_SCOPE_TOKEN);
        }
        if (!scope.equals(token.scope())) {
            throw new CoreFault(CoreErrorCode.CORE_1003_SCOPE_VIOLATION);
        }
        return token;
    }

    @ExceptionHandler(CoreFault.class)
    public ResponseEntity<Map<String, String>> coreFault(CoreFault fault) {
        int status = switch (fault.code()) {
            case CORE_1001_INVALID_CREDENTIAL, CORE_1002_INVALID_SCOPE_TOKEN -> HttpStatus.UNAUTHORIZED.value();
            case CORE_1003_SCOPE_VIOLATION -> HttpStatus.FORBIDDEN.value();
            case CORE_1004_NOT_FOUND -> HttpStatus.NOT_FOUND.value();
            case CORE_1005_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS.value();
            case CORE_1006_BAD_REQUEST -> HttpStatus.BAD_REQUEST.value();
        };
        return ResponseEntity.status(status)
                .body(Map.of("code", fault.code().code(), "message", fault.code().message()));
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
