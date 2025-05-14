package com.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class GeminiAIService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAIService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent";
    private final String LIST_MODELS_URL = "https://generativelanguage.googleapis.com/v1/models";
    private final RestTemplate restTemplate;

    public GeminiAIService() {
        this.restTemplate = new RestTemplate();
    }

    public String listAvailableModels() {
        try {
            String fullUrl = LIST_MODELS_URL + "?key=" + apiKey;
            logger.info("Listing models from: {}", fullUrl);

            ResponseEntity<Map> response = restTemplate.getForEntity(fullUrl, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody().toString();
            }
            return "Failed to get models list";
        } catch (Exception e) {
            logger.error("Error listing models", e);
            return "Error listing models: " + e.getMessage();
        }
    }

    public String processCustomerRequest(String request, String databaseContext) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", String.format(
                    "Based on the following database context:\n%s\n\nCustomer request: %s\n\nPlease provide a response:",
                    databaseContext,
                    request));

            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(textPart);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", parts);

            List<Map<String, Object>> contents = new ArrayList<>();
            contents.add(content);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", contents);

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            requestBody.put("generationConfig", generationConfig);

            String fullUrl = GEMINI_API_URL + "?key=" + apiKey;
            logger.info("Making request to: {}", fullUrl);
            logger.info("Request body: {}", requestBody);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                logger.info("Response body: {}", responseBody);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> firstCandidate = candidates.get(0);
                    Map<String, Object> content1 = (Map<String, Object>) firstCandidate.get("content");
                    List<Map<String, Object>> parts1 = (List<Map<String, Object>>) content1.get("parts");
                    if (parts1 != null && !parts1.isEmpty()) {
                        return (String) parts1.get(0).get("text");
                    }
                }
            }

            return "Failed to get response from AI";
        } catch (Exception e) {
            logger.error("Error processing request", e);
            return "Error processing request: " + e.getMessage();
        }
    }
}