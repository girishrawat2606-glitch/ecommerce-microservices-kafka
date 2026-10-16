package com.girish.ai.controller;

import com.girish.ai.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@CrossOrigin(origins = "*")
public class AiChatController {

    private final RagService ragService;

    public AiChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query must not be empty"));
        }

        long startTime = System.currentTimeMillis();
        String answer = ragService.askWithRag(query);
        long responseTimeMs = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(Map.of(
                "query", query,
                "answer", answer,
                "latencyMs", responseTimeMs,
                "status", "SUCCESS"
        ));
    }
}
