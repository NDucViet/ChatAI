package com.chatbot.service;

import com.chatbot.model.ChatResponse;
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
import jakarta.annotation.PostConstruct;

@Service
public class GeminiAIService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiAIService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private final String BASE_URL = "https://generativelanguage.googleapis.com";
    private final String API_VERSION = "v1";
    private final String MODEL_NAME = "models/gemini-2.0-flash";
    private final String LIST_MODELS_URL = BASE_URL + "/" + API_VERSION + "/models";
    private final RestTemplate restTemplate;

    public GeminiAIService() {
        this.restTemplate = new RestTemplate();
    }

    @PostConstruct
    public void init() {
        try {
            String modelsList = listAvailableModels();
            logger.info("Verifying model availability: {}", MODEL_NAME);

            if (!modelsList.contains(MODEL_NAME)) {
                logger.error("Model {} not found in available models: {}", MODEL_NAME, modelsList);
                throw new RuntimeException("Selected model is not available");
            } else {
                logger.info("Successfully verified model availability: {}", MODEL_NAME);
            }
        } catch (Exception e) {
            logger.error("Error initializing GeminiAIService", e);
            throw new RuntimeException("Failed to initialize GeminiAIService", e);
        }
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

    public ChatResponse processCustomerRequest(String request, String databaseContext, boolean isDatabaseQuery) {
        try {
            if (isDatabaseQuery) {
                return processDatabaseQuery(request, databaseContext);
            } else {
                return processGeneralKnowledgeQuery(request);
            }
        } catch (Exception e) {
            logger.error("Error in processCustomerRequest", e);
            return ChatResponse.error("Error processing request: " + e.getMessage());
        }
    }

    private ChatResponse processDatabaseQuery(String request, String databaseContext) {
        try {
            String prompt = String.format(
                    "You are a database assistant specializing in retrieving and explaining information from the database.\n\n"
                            +
                            "Database Context Available:\n%s\n\n" +
                            "User Question: %s\n\n" +
                            "Instructions:\n" +
                            "1. Focus ONLY on the information available in the database context\n" +
                            "2. If the exact information is not in the database, clearly state that\n" +
                            "3. Do not make assumptions or provide information outside the database\n" +
                            "4. Be precise and specific about which parts of the database you're referencing\n\n" +
                            "IMPORTANT: You must respond in Vietnamese and follow this exact format:\n" +
                            "1. Direct Answer: [Your answer based strictly on database information]\n" +
                            "2. Source: [Specify which exact parts of the database were used]\n" +
                            "3. Confidence: [A number between 0 and 1 indicating how well the database matches the query]\n",
                    databaseContext,
                    request);

            return executeGeminiRequest(prompt, "Database", 0.7);
        } catch (Exception e) {
            logger.error("Error processing database query", e);
            return ChatResponse.error("Lỗi khi xử lý truy vấn database: " + e.getMessage());
        }
    }

    private ChatResponse processGeneralKnowledgeQuery(String request) {
        try {
            String prompt = String.format(
                    "You are a knowledgeable AI assistant providing helpful and accurate information on general topics.\n\n"
                            +
                            "User Question: %s\n\n" +
                            "Instructions:\n" +
                            "1. Provide comprehensive and accurate information\n" +
                            "2. Use your general knowledge base to answer\n" +
                            "3. If uncertain, acknowledge limitations\n" +
                            "4. Provide context and explanations when helpful\n" +
                            "5. Stay factual and avoid speculation\n\n" +
                            "IMPORTANT: You must respond in Vietnamese and follow this exact format:\n" +
                            "1. Direct Answer: [Detailed answer in Vietnamese]\n" +
                            "2. Source: [General Knowledge]\n" +
                            "3. Confidence: [A number between 0 and 1]\n\n" +
                            "Remember to provide a complete and informative answer in Vietnamese language.",
                    request);

            return executeGeminiRequest(prompt, "General Knowledge", 0.9);
        } catch (Exception e) {
            logger.error("Error processing general knowledge query", e);
            return ChatResponse.error("Lỗi khi xử lý câu hỏi kiến thức chung: " + e.getMessage());
        }
    }

    private ChatResponse executeGeminiRequest(String prompt, String defaultSource, double temperature) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();

            Map<String, Object> promptContent = new HashMap<>();
            promptContent.put("text", prompt);

            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(promptContent);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", parts);

            requestBody.put("contents", List.of(content));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", temperature);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 8192); // Updated based on model specs
            requestBody.put("generationConfig", generationConfig);

            String fullUrl = BASE_URL + "/" + API_VERSION + "/" + MODEL_NAME + ":generateContent" + "?key=" + apiKey;
            logger.info("Making request to Gemini API with URL: {}", fullUrl);
            logger.debug("Request body: {}", requestBody);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                logger.debug("Response body: {}", responseBody);

                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");

                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> firstCandidate = candidates.get(0);
                    Map<String, Object> content1 = (Map<String, Object>) firstCandidate.get("content");
                    List<Map<String, Object>> parts1 = (List<Map<String, Object>>) content1.get("parts");

                    if (parts1 != null && !parts1.isEmpty()) {
                        String aiResponse = (String) parts1.get(0).get("text");
                        logger.info("Received response from Gemini: {}", aiResponse);

                        if (aiResponse == null || aiResponse.trim().isEmpty()) {
                            return ChatResponse.error("Không nhận được câu trả lời từ AI");
                        }

                        // Parse AI response to extract components
                        String answer = "";
                        String source = defaultSource;
                        double confidence = 0.0;

                        String[] lines = aiResponse.split("\n");
                        boolean foundAnswer = false;
                        StringBuilder fullAnswer = new StringBuilder();

                        for (String line : lines) {
                            if (line.startsWith("1. Direct Answer:")) {
                                answer = line.substring("1. Direct Answer:".length()).trim();
                                foundAnswer = true;
                            } else if (line.startsWith("2. Source:")) {
                                source = line.substring("2. Source:".length()).trim();
                            } else if (line.startsWith("3. Confidence:")) {
                                try {
                                    String confidenceStr = line.substring("3. Confidence:".length())
                                            .trim()
                                            .replaceAll("[^0-9.]", "");
                                    confidence = Double.parseDouble(confidenceStr);
                                } catch (NumberFormatException e) {
                                    logger.warn("Could not parse confidence value", e);
                                    confidence = 0.7; // Default confidence
                                }
                            } else if (foundAnswer) {
                                // Append any additional lines after "Direct Answer:" to the answer
                                fullAnswer.append(line).append("\n");
                            }
                        }

                        // If we found additional content, use it
                        if (fullAnswer.length() > 0) {
                            answer = answer + "\n" + fullAnswer.toString().trim();
                        }

                        // Validate the response
                        if (answer.trim().isEmpty()) {
                            logger.warn("Empty answer received from AI");
                            return ChatResponse.error("Không nhận được câu trả lời hợp lệ từ AI");
                        }

                        return ChatResponse.success(answer, source, confidence);
                    }
                }
            } else {
                logger.error("Error response from Gemini API: {}", response.getBody());
                return ChatResponse.error("Lỗi từ Gemini API: " + response.getStatusCode());
            }

            logger.error("Invalid response format from Gemini API");
            return ChatResponse.error("Định dạng câu trả lời không hợp lệ từ AI");
        } catch (Exception e) {
            logger.error("Error executing Gemini request", e);
            return ChatResponse.error("Lỗi khi thực hiện yêu cầu: " + e.getMessage());
        }
    }
}