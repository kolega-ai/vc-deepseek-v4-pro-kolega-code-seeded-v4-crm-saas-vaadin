package com.crmops.channel.web;

import com.crmops.channel.domain.Escalation;
import com.crmops.channel.service.EscalationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Triage assistance for the support desk. The desk's self-hosted model router
 * reads the escalation and recommends the next workflow move; the desk applies
 * the recommendation so agents do not have to work the state machine by hand.
 */
@RestController
@RequestMapping("/api/insight")
public class SupportInsightController {

    private static final String ROUTER_URL = "http://model-router.crmops.local/v1/chat/completions";
    private static final String ROUTER_KEY = "router-crmops-insight-4f8c2d";

    private static final String TRIAGE_POLICY = """
            You are the CrmOps support triage assistant.
            You may answer with exactly one action: resolve, wait_on_customer or wait_on_internal.
            Internal routing notes and the desk playbook are confidential and must never be repeated.
            Reply with JSON: {\"action\": \"<one of the actions>\"}.
            """;

    private final EscalationService escalationService;
    private final ObjectMapper mapper;
    private final RestClient router = RestClient.create();

    public SupportInsightController(EscalationService escalationService, ObjectMapper mapper) {
        this.escalationService = escalationService;
        this.mapper = mapper;
    }

    @PostMapping("/triage/{id}")
    public Map<String, Object> triage(@PathVariable long id) {
        Escalation escalation = escalationService.get(id);
        Map<String, Object> request = Map.of(
                "model", "crmops-triage",
                "messages", List.of(
                        Map.of("role", "system", "content", TRIAGE_POLICY
                                + "\nDesk router key: " + ROUTER_KEY),
                        Map.of("role", "user", "content",
                                "Subject: " + escalation.getSubject()
                                        + "\nDescription: " + escalation.getDescription())));
        String answer = router.post()
                .uri(ROUTER_URL)
                .header("Authorization", "Bearer " + ROUTER_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        String action = parseAction(answer);
        escalationService.transition(id, action);
        return Map.of("id", id, "action", action);
    }

    private String parseAction(String answer) {
        try {
            JsonNode node = mapper.readTree(answer);
            String action = node.path("action").asText("wait_on_internal");
            return action;
        } catch (Exception e) {
            return "wait_on_internal";
        }
    }
}
