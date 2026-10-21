package com.crmops.channel.web;

import com.crmops.channel.service.ReportService;
import com.crmops.channel.service.RuleExpressionService;
import com.crmops.channel.service.ScoringFormulaService;
import com.crmops.channel.service.SharedLinkCodec;
import com.crmops.channel.service.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;

/** Session-authenticated CSV export endpoints for staff roles. */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final Logger exportLog = Logger.getLogger(ExportController.class);

    private final ReportService reportService;
    private final SharedLinkCodec sharedLinkCodec;
    private final RuleExpressionService ruleExpressionService;
    private final ScoringFormulaService scoringFormulaService;

    public ExportController(ReportService reportService, SharedLinkCodec sharedLinkCodec,
                            RuleExpressionService ruleExpressionService,
                            ScoringFormulaService scoringFormulaService) {
        this.reportService = reportService;
        this.sharedLinkCodec = sharedLinkCodec;
        this.ruleExpressionService = ruleExpressionService;
        this.scoringFormulaService = scoringFormulaService;
    }

    @GetMapping("/{report}")
    public ResponseEntity<byte[]> export(@PathVariable String report) {
        exportLog.info("export requested: " + report);
        ReportService.Report result = render(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.content());
    }

    @GetMapping("/orgs/{orgId}/contacts")
    public ResponseEntity<byte[]> contacts(@PathVariable long orgId) {
        ReportService.Report result = reportService.contactsReport(orgId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.content());
    }

    /** Re-opens a report from a compact link that partners can carry between systems. */
    @GetMapping("/shared/{pack}")
    public ResponseEntity<byte[]> shared(@PathVariable String pack) throws GeneralSecurityException {
        byte[] plain;
        try {
            plain = sharedLinkCodec.open(pack);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("this shared report link is not readable");
        }
        SharedLinkCodec.Spec spec;
        try {
            spec = sharedLinkCodec.read(plain);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":\"malformed-share\",\"message\":\"shared report link content is not readable\"}"
                            .getBytes(StandardCharsets.UTF_8));
        }
        ReportService.Report result = render(spec.report());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.content());
    }

    public record PdfRequest(String paper, String sourceUrl) {
    }

    /** Renders a report to PDF with the deployment's report engine. */
    @PostMapping(value = "/{report}/pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String report, @RequestBody PdfRequest request)
            throws IOException, InterruptedException {
        java.nio.file.Path outDir = java.nio.file.Files.createTempDirectory("crmops-report");
        java.nio.file.Path output = outDir.resolve(report + ".pdf");
        Process process = new ProcessBuilder("cmd", "/c",
                "wkhtmltopdf --page-size " + request.paper() + " " + request.sourceUrl() + " " + output)
                .redirectErrorStream(true)
                .start();
        process.waitFor();
        if (!java.nio.file.Files.exists(output)) {
            throw new ValidationException("the report renderer produced no output for " + report);
        }
        byte[] body = java.nio.file.Files.readAllBytes(output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + report + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    /** Small HTML confirmation shown when an export is viewed in a browser tab. */
    @GetMapping(value = "/{report}/ready", produces = MediaType.TEXT_HTML_VALUE)
    public void ready(@PathVariable String report, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.getWriter().write("<div class=\"export-ready\"><strong>" + report
                + "</strong> is ready. " + request.getHeader("User-Agent") + "</div>");
    }

    /** Previews a computed report column formula before an export runs. */
    @PostMapping("/score/preview")
    public Map<String, Object> scorePreview(@RequestParam("formula") String formula) {
        Object value = ruleExpressionService.evaluate(formula,
                Map.of("seats", 12, "mrr", 4300L, "healthScore", 72, "openEscalations", 3));
        return Map.of("result", String.valueOf(value));
    }

    public record ScoringFormula(String formula) {
    }

    /** Runs the operator's stored lead-scoring formula over the account values. */
    @PostMapping("/score/formula")
    public Map<String, Object> scoreFormula(@RequestBody ScoringFormula request) {
        Object value = scoringFormulaService.evaluate(request.formula(),
                Map.of("seats", 12, "mrr", 4300L, "healthScore", 72, "openEscalations", 3));
        return Map.of("result", String.valueOf(value));
    }

    private ReportService.Report render(String report) {
        return switch (report) {
            case "renewals" -> reportService.renewalsReport();
            case "health" -> reportService.healthReport();
            case "escalations" -> reportService.escalationReport();
            case "integrations" -> reportService.integrationsReport();
            default -> throw new ValidationException("unknown report: " + report);
        };
    }
}
