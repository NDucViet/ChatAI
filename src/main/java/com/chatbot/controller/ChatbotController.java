package com.chatbot.controller;

import com.chatbot.service.GeminiAIService;
import com.chatbot.service.DatabaseService;
import com.chatbot.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ChatbotController {

    private final GeminiAIService geminiAIService;
    private final DatabaseService databaseService;

    @Autowired
    public ChatbotController(GeminiAIService geminiAIService, DatabaseService databaseService) {
        this.geminiAIService = geminiAIService;
        this.databaseService = databaseService;
    }

    @GetMapping("/models")
    public ResponseEntity<String> listModels() {
        return ResponseEntity.ok(geminiAIService.listAvailableModels());
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> processRequest(@RequestBody ChatRequest request) {
        // Kiểm tra xem câu hỏi có liên quan đến database không
        boolean isDatabaseQuery = request.getIsDatabaseQuery() == 1;

        // Chỉ lấy context từ database nếu là câu hỏi liên quan đến database
        String databaseContext = isDatabaseQuery ? databaseService.getRelevantDataForQuery(request.getQuestion()) : "";

        // Xử lý câu hỏi với loại phù hợp
        ChatResponse response = geminiAIService.processCustomerRequest(
                request.getQuestion(),
                databaseContext,
                isDatabaseQuery);

        return ResponseEntity.ok(response);
    }

}

class ChatRequest {
    private String question;
    private int isDatabaseQuery;

    public String getQuestion() {
        return question;
    }

    public int getIsDatabaseQuery() {
        return isDatabaseQuery;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public void setIsDatabaseQuery(int isDatabaseQuery) {
        this.isDatabaseQuery = isDatabaseQuery;
    }
}