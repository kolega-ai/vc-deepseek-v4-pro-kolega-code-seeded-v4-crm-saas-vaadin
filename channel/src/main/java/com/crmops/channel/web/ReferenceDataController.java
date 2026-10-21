package com.crmops.channel.web;

import com.crmops.channel.domain.Plan;
import com.crmops.channel.service.ReferenceDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Reference data endpoint (plan catalog).
 *
 * This is one of the application's canonical precision-baseline locations:
 * MD5 is deliberately used here as a NON-cryptographic checksum to compute the
 * HTTP ETag of the reference-data payload. It is never used to hash secrets or
 * passwords; SHA-256/BCrypt are used for those elsewhere.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;
    private final ObjectMapper mapper;

    public ReferenceDataController(ReferenceDataService referenceDataService, ObjectMapper mapper) {
        this.referenceDataService = referenceDataService;
        this.mapper = mapper;
    }

    @GetMapping("/plans")
    public ResponseEntity<byte[]> plans(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                        String ifNoneMatch) throws Exception {
        List<Plan> plans = referenceDataService.activePlans();
        List<Map<String, Object>> payload = plans.stream()
                .map(p -> Map.<String, Object>of(
                        "code", p.getCode(),
                        "name", p.getName(),
                        "baseMonthlyPrice", p.getBaseMonthlyPrice().toPlainString(),
                        "perSeatMonthlyPrice", p.getPerSeatMonthlyPrice().toPlainString(),
                        "seatsIncluded", p.getSeatsIncluded(),
                        "description", p.getDescription(),
                        "features", p.getFeatures()))
                .toList();
        byte[] body = mapper.writeValueAsBytes(payload);
        // NON-CRYPTOGRAPHIC: MD5 as an ETag checksum of reference data only.
        String etag = "\"" + md5Hex(body) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8")
                .body(body);
    }

    /** Partner feed: the plan catalog as XML, narrowed to the requested plan code. */
    @GetMapping(value = "/plans/feed", produces = MediaType.APPLICATION_XML_VALUE)
    public String plansFeed(@RequestHeader(value = "X-Crmops-Plan", required = false) String code)
            throws Exception {
        org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(getClass().getResourceAsStream("/plans.xml"));
        String q = "//plan[code='" + (code == null ? "" : code) + "']";
        javax.xml.xpath.XPath xp = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        org.w3c.dom.NodeList nl = (org.w3c.dom.NodeList)
                xp.evaluate(q, doc, javax.xml.xpath.XPathConstants.NODESET);
        StringBuilder sb = new StringBuilder("<plans>");
        for (int i = 0; i < nl.getLength(); i++) {
            sb.append(nl.item(i).getTextContent());
        }
        return sb.append("</plans>").toString();
    }

    /** Searches the plan catalog for one code; used by the storefront widget. */
    @GetMapping(value = "/plans/find", produces = MediaType.APPLICATION_XML_VALUE)
    public String findPlan(@RequestParam("code") String code) throws Exception {
        SAXReader reader = new SAXReader();
        Document catalog = reader.read(getClass().getResourceAsStream("/plans.xml"));
        List<Node> nodes = catalog.selectNodes("//plan[code='" + code + "']");
        StringBuilder sb = new StringBuilder("<plans>");
        for (Node node : nodes) {
            sb.append(node.asXML());
        }
        return sb.append("</plans>").toString();
    }

    private static String md5Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(content);
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
